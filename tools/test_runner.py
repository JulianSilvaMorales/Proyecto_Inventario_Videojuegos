"""Ejecución de todas las suites de prueba con logs y resumen (comando `probar`).

Cada ejecución crea `logs/tests/<AAAAMMDD-HHMMSS>/` con la salida de consola de
cada suite (`<suite>.log`), sus reportes JUnit XML (`<suite>/`) y `resumen.md`.
"""

from __future__ import annotations

import base64
import os
import secrets
import shutil
import signal
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from collections.abc import Iterator
from contextlib import ExitStack, contextmanager
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Any

SUITES = ("python", "backend", "frontend", "e2e")
WINDOWS = os.name == "nt"


@dataclass
class Paso:
    nombre: str
    codigo: int
    segundos: float


@dataclass
class ResultadoSuite:
    suite: str
    pasos: list[Paso] = field(default_factory=list)
    pruebas: int = 0
    fallos: int = 0
    errores: int = 0
    omitidas: int = 0
    segundos: float = 0.0
    nota: str = ""

    @property
    def correcta(self) -> bool:
        return (
            bool(self.pasos)
            and all(paso.codigo == 0 for paso in self.pasos)
            and not (self.fallos or self.errores)
        )


class EntornoError(RuntimeError):
    """El entorno de una suite (contenedor, servidor) no se pudo preparar."""


def _ejecutable(nombre: str) -> str:
    ruta = shutil.which(nombre)
    if ruta is None:
        raise EntornoError(f"No se encontró '{nombre}' en el PATH.")
    return ruta


def _maven(root: Path) -> list[str]:
    if WINDOWS:
        return [str(root / "backend" / "mvnw.cmd")]
    # Con `sh` no depende del permiso de ejecución del archivo en el repositorio.
    return ["sh", str(root / "backend" / "mvnw")]


def _ejecutar(
    resultado: ResultadoSuite,
    nombre: str,
    comando: list[str],
    log: Path,
    cwd: Path,
    env: dict[str, str] | None = None,
) -> int:
    inicio = time.monotonic()
    with log.open("a", encoding="utf-8") as salida:
        salida.write(f"\n===== {nombre}: {' '.join(comando)} (en {cwd})\n")
        salida.flush()
        try:
            codigo = subprocess.run(
                comando,
                cwd=cwd,
                env=env,
                stdout=salida,
                stderr=subprocess.STDOUT,
                check=False,
            ).returncode
        except OSError as exc:
            salida.write(f"No se pudo ejecutar: {exc}\n")
            codigo = 127
        salida.write(f"===== {nombre}: código de salida {codigo}\n")
    resultado.pasos.append(Paso(nombre, codigo, time.monotonic() - inicio))
    return codigo


def contar_junit(directorio: Path) -> tuple[int, int, int, int]:
    """Suma pruebas, fallos, errores y omitidas de los `<testsuite>` hoja de los XML."""
    totales = [0, 0, 0, 0]
    for archivo in sorted(directorio.rglob("*.xml")):
        try:
            raiz = ET.parse(archivo).getroot()
        except ET.ParseError:
            continue
        suites = [raiz] if raiz.tag == "testsuite" else raiz.iter("testsuite")
        for suite in suites:
            if suite.find("testsuite") is not None:
                continue
            for indice, atributo in enumerate(("tests", "failures", "errors", "skipped")):
                totales[indice] += int(float(suite.get(atributo, "0") or 0))
    return totales[0], totales[1], totales[2], totales[3]


def _suite_python(root: Path, carpeta: Path, log: Path, resultado: ResultadoSuite) -> None:
    junit = carpeta / "python"
    junit.mkdir(parents=True, exist_ok=True)
    python = sys.executable
    _ejecutar(resultado, "ruff check", [python, "-m", "ruff", "check", "tools"], log, root)
    _ejecutar(
        resultado, "ruff format", [python, "-m", "ruff", "format", "--check", "tools"], log, root
    )
    _ejecutar(
        resultado,
        "pytest",
        [python, "-m", "pytest", f"--junitxml={junit / 'pytest-junit.xml'}"],
        log,
        root,
    )


def _suite_backend(root: Path, carpeta: Path, log: Path, resultado: ResultadoSuite) -> None:
    backend = root / "backend"
    _ejecutar(resultado, "mvn clean verify", [*_maven(root), "-B", "clean", "verify"], log, backend)
    destino = carpeta / "backend"
    destino.mkdir(parents=True, exist_ok=True)
    for reporte in (backend / "target" / "surefire-reports").glob("TEST-*.xml"):
        shutil.copy2(reporte, destino / reporte.name)


def _suite_frontend(root: Path, carpeta: Path, log: Path, resultado: ResultadoSuite) -> None:
    frontend = root / "frontend"
    npm = _ejecutable("npm")
    npx = _ejecutable("npx")
    junit = carpeta / "frontend"
    junit.mkdir(parents=True, exist_ok=True)
    _ejecutar(resultado, "npm ci", [npm, "ci", "--no-audit", "--no-fund"], log, frontend)
    _ejecutar(resultado, "lint", [npm, "run", "lint"], log, frontend)
    _ejecutar(resultado, "typecheck", [npm, "run", "typecheck"], log, frontend)
    _ejecutar(
        resultado,
        "vitest",
        [
            npx,
            "vitest",
            "run",
            "--reporter=default",
            "--reporter=junit",
            f"--outputFile.junit={junit / 'vitest-junit.xml'}",
        ],
        log,
        frontend,
    )
    _ejecutar(resultado, "build", [npm, "run", "build"], log, frontend)


def _puerto_libre() -> int:
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return int(sock.getsockname()[1])


def _esperar_url(url: str, proceso: subprocess.Popen[Any], segundos: int = 180) -> None:
    limite = time.monotonic() + segundos
    while time.monotonic() < limite:
        if proceso.poll() is not None:
            raise EntornoError(f"El proceso terminó antes de responder en {url}.")
        try:
            with urllib.request.urlopen(url, timeout=3) as respuesta:
                if respuesta.status == 200:
                    return
        except (urllib.error.URLError, ConnectionError, TimeoutError):
            pass
        time.sleep(1)
    raise EntornoError(f"Sin respuesta de {url} tras {segundos} s.")


def _detener(proceso: subprocess.Popen[Any]) -> None:
    if proceso.poll() is not None:
        return
    if WINDOWS:
        subprocess.run(
            ["taskkill", "/PID", str(proceso.pid), "/T", "/F"], capture_output=True, check=False
        )
    else:
        os.killpg(proceso.pid, signal.SIGTERM)
    try:
        proceso.wait(timeout=30)
    except subprocess.TimeoutExpired:
        proceso.kill()


@contextmanager
def _servicio(
    comando: list[str], cwd: Path, env: dict[str, str], log: Path
) -> Iterator[subprocess.Popen[Any]]:
    salida = log.open("a", encoding="utf-8")
    salida.write(f"\n===== servicio: {' '.join(comando)} (en {cwd})\n")
    salida.flush()
    proceso = subprocess.Popen(
        comando,
        cwd=cwd,
        env=env,
        stdout=salida,
        stderr=subprocess.STDOUT,
        start_new_session=not WINDOWS,
    )
    try:
        yield proceso
    finally:
        _detener(proceso)
        salida.close()


def _jar_backend(root: Path) -> Path:
    candidatos = [
        jar
        for jar in (root / "backend" / "target").glob("*.jar")
        if not jar.name.endswith("-plain.jar")
    ]
    if not candidatos:
        raise EntornoError("No se encontró el JAR del backend en backend/target.")
    return max(candidatos, key=lambda jar: jar.stat().st_mtime)


def _preparar_playwright(root: Path, log: Path, resultado: ResultadoSuite) -> bool:
    npm = _ejecutable("npm")
    npx = _ejecutable("npx")
    if _ejecutar(
        resultado, "npm ci e2e", [npm, "ci", "--no-audit", "--no-fund"], log, root / "e2e"
    ):
        return False
    return not _ejecutar(
        resultado,
        "navegador Playwright",
        [npx, "playwright", "install", "chromium"],
        log,
        root / "e2e",
    )


def _playwright(
    root: Path,
    log: Path,
    resultado: ResultadoSuite,
    reportes: Path,
    url_web: str,
    url_api: str,
    admin: tuple[str, str],
) -> None:
    _ejecutar(
        resultado,
        "playwright",
        [_ejecutable("npx"), "playwright", "test"],
        log,
        root / "e2e",
        {
            **os.environ,
            "E2E_BASE_URL": url_web,
            "E2E_API_URL": url_api,
            "E2E_ADMIN_EMAIL": admin[0],
            "E2E_ADMIN_PASSWORD": admin[1],
            "E2E_REPORT_DIR": str(reportes),
        },
    )


def _suite_e2e(root: Path, carpeta: Path, log: Path, resultado: ResultadoSuite) -> None:
    if __package__:
        from .database import crear_base_y_sembrar
        from .postgres_desechable import docker_disponible, postgres_temporal
    else:
        from database import crear_base_y_sembrar
        from postgres_desechable import docker_disponible, postgres_temporal

    if not docker_disponible():
        raise EntornoError("Docker no está disponible para la base desechable de la prueba E2E.")
    npm = _ejecutable("npm")
    npx = _ejecutable("npx")
    java = _ejecutable("java")
    reportes = carpeta / "e2e"
    reportes.mkdir(parents=True, exist_ok=True)
    admin_email = "admin-e2e@inventario.invalid"
    admin_password = secrets.token_urlsafe(18)
    puerto_api = _puerto_libre()
    puerto_web = _puerto_libre()
    url_api = f"http://127.0.0.1:{puerto_api}"
    url_web = f"http://127.0.0.1:{puerto_web}"

    if _ejecutar(
        resultado,
        "empaquetar backend",
        [*_maven(root), "-B", "-q", "-DskipTests", "package"],
        log,
        root / "backend",
    ):
        return
    entorno_front = {**os.environ, "VITE_API_URL": url_api}
    if _ejecutar(
        resultado, "build frontend", [npm, "run", "build"], log, root / "frontend", entorno_front
    ):
        return
    if not _preparar_playwright(root, log, resultado):
        return

    with ExitStack() as pila:
        base = pila.enter_context(postgres_temporal("inventario_e2e", prefijo="inventario-e2e"))
        inicio = time.monotonic()
        with log.open("a", encoding="utf-8") as salida:
            salida.write("\n===== crear-bd sobre la base desechable\n")
            salida.write(f"{crear_base_y_sembrar(root, base, admin_email, admin_password)}\n")
        resultado.pasos.append(Paso("crear-bd", 0, time.monotonic() - inicio))

        entorno_api = {
            **os.environ,
            "DB_HOST": base["host"],
            "DB_PORT": str(base["port"]),
            "DB_NAME": base["dbname"],
            "DB_USER": base["user"],
            "DB_PASSWORD": base["password"],
            "JWT_SECRET": base64.b64encode(secrets.token_bytes(48)).decode("ascii"),
            "SERVER_PORT": str(puerto_api),
            "CORS_ORIGINS": url_web,
            "SPRING_PROFILES_ACTIVE": "local",
        }
        api = pila.enter_context(
            _servicio([java, "-jar", str(_jar_backend(root))], root, entorno_api, log)
        )
        _esperar_url(f"{url_api}/actuator/health", api)
        web = pila.enter_context(
            _servicio(
                [
                    npx,
                    "vite",
                    "preview",
                    "--host",
                    "127.0.0.1",
                    "--port",
                    str(puerto_web),
                    "--strictPort",
                ],
                root / "frontend",
                dict(os.environ),
                log,
            )
        )
        _esperar_url(url_web, web)
        _playwright(root, log, resultado, reportes, url_web, url_api, (admin_email, admin_password))


def _suite_e2e_compose(root: Path, carpeta: Path, log: Path, resultado: ResultadoSuite) -> None:
    """E2E sobre `docker-compose.yml` en un proyecto aislado que se elimina al terminar."""
    docker = _ejecutable("docker")
    reportes = carpeta / "e2e"
    reportes.mkdir(parents=True, exist_ok=True)
    if not _preparar_playwright(root, log, resultado):
        return
    proyecto = f"pixelvault-e2e-{secrets.token_hex(4)}"
    admin = ("admin-e2e@inventario.invalid", secrets.token_urlsafe(18))
    puerto_api = _puerto_libre()
    puerto_web = _puerto_libre()
    # Las variables del proceso tienen prioridad sobre el .env del desarrollador.
    entorno = {
        **os.environ,
        "COMPOSE_PROJECT_NAME": proyecto,
        "DB_NAME": "inventario",
        "DB_USER": "inventario",
        "DB_PASSWORD": secrets.token_urlsafe(18),
        "ADMIN_EMAIL": admin[0],
        "ADMIN_PASSWORD": admin[1],
        "JWT_SECRET": base64.b64encode(secrets.token_bytes(48)).decode("ascii"),
        "BACKEND_PORT": str(puerto_api),
        "FRONTEND_PORT": str(puerto_web),
    }
    compose = [docker, "compose", "-f", str(root / "docker-compose.yml")]
    try:
        if _ejecutar(
            resultado,
            "docker compose up --build --wait",
            [*compose, "up", "--build", "--detach", "--wait"],
            log,
            root,
            entorno,
        ):
            return
        # El backend solo acepta el origen "localhost" configurado en CORS_ORIGINS.
        _playwright(
            root,
            log,
            resultado,
            reportes,
            f"http://localhost:{puerto_web}",
            f"http://localhost:{puerto_api}",
            admin,
        )
    finally:
        with log.open("a", encoding="utf-8") as salida:
            salida.write("\n===== docker compose logs\n")
            salida.flush()
            subprocess.run(
                [*compose, "logs", "--no-color"],
                cwd=root,
                env=entorno,
                stdout=salida,
                stderr=subprocess.STDOUT,
                check=False,
            )
        _ejecutar(
            resultado, "docker compose down -v", [*compose, "down", "--volumes"], log, root, entorno
        )


def _despertar(url: str, segundos: int = 300) -> None:
    """Los servicios gratuitos de Render se suspenden sin tráfico y tardan en despertar."""
    limite = time.monotonic() + segundos
    while time.monotonic() < limite:
        try:
            with urllib.request.urlopen(url, timeout=30) as respuesta:
                if respuesta.status == 200:
                    return
        except (urllib.error.URLError, ConnectionError, TimeoutError):
            pass
        time.sleep(5)
    raise EntornoError(f"Sin respuesta de {url} tras {segundos} s.")


def _suite_e2e_remoto(root: Path, carpeta: Path, log: Path, resultado: ResultadoSuite) -> None:
    """E2E contra un despliegue ya publicado (por ejemplo, Render). No levanta nada:
    toma las URL y el administrador de E2E_BASE_URL, E2E_API_URL, E2E_ADMIN_EMAIL y
    E2E_ADMIN_PASSWORD. Crea datos de prueba (tipo, categorías, proveedor, producto,
    orden, entrada y una venta) en esa base."""
    url_web = os.environ.get("E2E_BASE_URL", "").rstrip("/")
    url_api = os.environ.get("E2E_API_URL", "").rstrip("/")
    admin = (os.environ.get("E2E_ADMIN_EMAIL", ""), os.environ.get("E2E_ADMIN_PASSWORD", ""))
    faltan = [
        nombre
        for nombre, valor in (
            ("E2E_BASE_URL", url_web),
            ("E2E_API_URL", url_api),
            ("E2E_ADMIN_EMAIL", admin[0]),
            ("E2E_ADMIN_PASSWORD", admin[1]),
        )
        if not valor
    ]
    if faltan:
        raise EntornoError("Para la E2E remota defina: " + ", ".join(faltan) + ".")
    reportes = carpeta / "e2e"
    reportes.mkdir(parents=True, exist_ok=True)
    with log.open("a", encoding="utf-8") as salida:
        salida.write(f"E2E remota: web {url_web}, API {url_api}\n")
    _despertar(f"{url_api}/actuator/health")
    _despertar(url_web)
    if not _preparar_playwright(root, log, resultado):
        return
    _playwright(root, log, resultado, reportes, url_web, url_api, admin)


EJECUTORES = {
    "python": _suite_python,
    "backend": _suite_backend,
    "frontend": _suite_frontend,
    "e2e": _suite_e2e,
}


def _git(root: Path, *argumentos: str) -> str:
    try:
        return subprocess.run(
            ["git", *argumentos], cwd=root, capture_output=True, text=True, check=False
        ).stdout.strip()
    except OSError:
        return ""


def escribir_resumen(carpeta: Path, resultados: list[ResultadoSuite], root: Path) -> Path:
    commit = _git(root, "rev-parse", "--short", "HEAD") or "sin commit"
    cambios = " (con cambios sin confirmar)" if _git(root, "status", "--porcelain") else ""
    filas = [
        f"# Resumen de pruebas — {carpeta.name}",
        "",
        f"- Commit: `{commit}`{cambios}",
        f"- Estado general: **{'CORRECTO' if all(r.correcta for r in resultados) else 'FALLIDO'}**",
        "",
        "| Suite | Estado | Pruebas | Fallos | Errores | Omitidas | Duración | Log |",
        "|---|---|---:|---:|---:|---:|---:|---|",
    ]
    for r in resultados:
        estado = "✅ correcto" if r.correcta else "❌ fallido"
        filas.append(
            f"| {r.suite} | {estado} | {r.pruebas} | {r.fallos} | {r.errores} | {r.omitidas} "
            f"| {r.segundos:.0f} s | [{r.suite}.log]({r.suite}.log) |"
        )
    filas += ["", "## Pasos", ""]
    for r in resultados:
        filas.append(f"### {r.suite}")
        if r.nota:
            filas.append(f"- ⚠️ {r.nota}")
        for paso in r.pasos:
            marca = "✅" if paso.codigo == 0 else f"❌ (código {paso.codigo})"
            filas.append(f"- {paso.nombre}: {marca} — {paso.segundos:.0f} s")
        filas.append("")
    filas.append("Los reportes JUnit XML de cada suite están en su subcarpeta.")
    resumen = carpeta / "resumen.md"
    resumen.write_text("\n".join(filas) + "\n", encoding="utf-8")
    # En GitHub Actions el resumen también se muestra en la página de la ejecución.
    resumen_ci = os.environ.get("GITHUB_STEP_SUMMARY")
    if resumen_ci:
        with Path(resumen_ci).open("a", encoding="utf-8") as salida:
            salida.write("\n".join(filas) + "\n")
    return resumen


ENTORNOS_E2E = ("local", "compose", "remoto")


def probar(
    root: Path, suites: list[str], dry_run: bool = False, e2e_entorno: str = "local"
) -> tuple[int, Path | None]:
    desconocidas = [suite for suite in suites if suite not in SUITES]
    if desconocidas:
        raise ValueError(f"Suites desconocidas: {', '.join(desconocidas)}.")
    if e2e_entorno not in ENTORNOS_E2E:
        raise ValueError(f"Entorno E2E desconocido: {e2e_entorno}.")
    if dry_run:
        print("Se ejecutarían las suites: " + ", ".join(suites) + ". No se ejecutó nada.")
        return 0, None

    carpeta = root / "logs" / "tests" / datetime.now().strftime("%Y%m%d-%H%M%S")
    carpeta.mkdir(parents=True, exist_ok=True)
    resultados: list[ResultadoSuite] = []
    for suite in suites:
        resultado = ResultadoSuite(suite)
        log = carpeta / f"{suite}.log"
        log.touch()
        print(f"[{suite}] en ejecución… (log: {log})", flush=True)
        inicio = time.monotonic()
        try:
            ejecutor = EJECUTORES[suite]
            if suite == "e2e" and e2e_entorno == "compose":
                ejecutor = _suite_e2e_compose
            elif suite == "e2e" and e2e_entorno == "remoto":
                ejecutor = _suite_e2e_remoto
            ejecutor(root, carpeta, log, resultado)
        except (EntornoError, OSError, subprocess.SubprocessError, TimeoutError) as exc:
            resultado.nota = str(exc)
            resultado.pasos.append(Paso("preparación del entorno", 1, 0.0))
            with log.open("a", encoding="utf-8") as salida:
                salida.write(f"\nERROR DE ENTORNO: {exc}\n")
        resultado.segundos = time.monotonic() - inicio
        (
            resultado.pruebas,
            resultado.fallos,
            resultado.errores,
            resultado.omitidas,
        ) = contar_junit(carpeta / suite)
        resultados.append(resultado)
        estado = "correcto" if resultado.correcta else "FALLIDO"
        print(
            f"[{suite}] {estado}: {resultado.pruebas} pruebas, {resultado.fallos} fallos, "
            f"{resultado.errores} errores, {resultado.omitidas} omitidas "
            f"({resultado.segundos:.0f} s)",
            flush=True,
        )

    resumen = escribir_resumen(carpeta, resultados, root)
    return (0 if all(r.correcta for r in resultados) else 1), resumen
