"""CLI único para las herramientas de desarrollo del inventario."""

from __future__ import annotations

import argparse
import json
import os
import sys
from datetime import UTC, datetime
from pathlib import Path

import psycopg
from jinja2.exceptions import TemplateError

if __package__:
    from .database import (
        _migrations,
        crear_base_y_sembrar,
        parametros_conexion,
        reiniciar_base_local,
        sembrar_base,
    )
    from .env_config import cargar_env_file
    from .model_validator import validar_modelo
    from .sql_generator import generar_archivos
    from .test_runner import ENTORNOS_E2E, SUITES, probar
else:
    from database import (
        _migrations,
        crear_base_y_sembrar,
        parametros_conexion,
        reiniciar_base_local,
        sembrar_base,
    )
    from env_config import cargar_env_file
    from model_validator import validar_modelo
    from sql_generator import generar_archivos
    from test_runner import ENTORNOS_E2E, SUITES, probar

COMANDOS = (
    "validar",
    "generar",
    "crear-bd",
    "reiniciar-bd",
    "sembrar",
    "probar",
    "todo",
)


def crear_parser() -> argparse.ArgumentParser:
    """Construye el parser y expone los comandos planificados."""
    parser = argparse.ArgumentParser(
        description="Herramientas de desarrollo del sistema de inventario.",
    )
    subparsers = parser.add_subparsers(dest="comando", required=True)

    descripciones = {
        "validar": "Validar el modelo JSON y sus referencias.",
        "generar": "Generar SQL, metadatos y CRUD JDBC desde el modelo JSON.",
        "crear-bd": "Crear la base de datos, aplicar migraciones y cargar semillas.",
        "reiniciar-bd": "Recrear la base de datos local.",
        "sembrar": "Cargar las semillas de datos.",
        "probar": "Ejecutar las pruebas del proyecto.",
        "todo": "Validar, generar, crear la base de datos y ejecutar las pruebas.",
    }

    for comando in COMANDOS:
        subparser = subparsers.add_parser(comando, help=descripciones[comando])
        subparser.add_argument(
            "--dry-run",
            action="store_true",
            help="Mostrar la operación prevista sin modificar datos.",
        )
        if comando == "validar":
            subparser.add_argument(
                "--model-dir",
                type=Path,
                help="Directorio con los módulos JSON (por defecto db/model/modulos).",
            )
            subparser.add_argument(
                "--schema",
                type=Path,
                help="Ruta del esquema JSON (por defecto db/model/model.schema.json).",
            )
            subparser.add_argument(
                "--log-dir",
                type=Path,
                help="Directorio de logs (por defecto logs/tools).",
            )
        if comando in {"probar", "todo"}:
            subparser.add_argument(
                "--suites",
                default=",".join(SUITES),
                help=(
                    "Suites separadas por comas (por defecto todas: "
                    + ", ".join(SUITES)
                    + "). La suite e2e requiere Docker, Java, Node y Maven."
                ),
            )
            subparser.add_argument(
                "--e2e-entorno",
                choices=ENTORNOS_E2E,
                default="local",
                help=(
                    "Dónde se ejecuta la E2E: 'local' (backend JAR y vite preview) o "
                    "'compose' (docker-compose.yml en un proyecto aislado)."
                ),
            )
        if comando == "reiniciar-bd":
            subparser.add_argument(
                "--confirm-db",
                help="Nombre exacto de la base local que se autoriza borrar y recrear.",
            )

    return parser


def _validar(args: argparse.Namespace) -> int:
    root = Path(__file__).resolve().parents[1]
    model_dir = args.model_dir or root / "db" / "model" / "modulos"
    schema_path = args.schema or root / "db" / "model" / "model.schema.json"
    log_dir = args.log_dir or root / "logs" / "tools"
    errors, entity_count = validar_modelo(model_dir, schema_path)
    timestamp = datetime.now(UTC).strftime("%Y%m%dT%H%M%S%fZ")
    log_path = log_dir / f"validar-{timestamp}.log"
    result = (
        f"Modelo válido: {entity_count} entidades."
        if not errors
        else f"Validación fallida: {len(errors)} error(es)."
    )
    log_content = "\n".join([result, *errors]) + "\n"

    try:
        log_dir.mkdir(parents=True, exist_ok=True)
        log_path.write_text(log_content, encoding="utf-8")
    except OSError as exc:
        print(f"No se pudo guardar el log de validación '{log_path}': {exc}", file=sys.stderr)
        return 1

    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        print(f"Validación fallida. Log: {log_path}", file=sys.stderr)
        return 1

    modo = " (dry-run)" if args.dry_run else ""
    print(f"{result}{modo} Log: {log_path}")
    return 0


def _escribir_log(root: Path, command: str, status: str, details: str) -> Path | None:
    log_dir = root / "logs" / "tools"
    timestamp = datetime.now(UTC).strftime("%Y%m%dT%H%M%S%fZ")
    log_path = log_dir / f"{command}-{timestamp}.log"
    try:
        log_dir.mkdir(parents=True, exist_ok=True)
        log_path.write_text(f"{status}\n{details}\n", encoding="utf-8")
    except OSError as exc:
        print(f"No se pudo guardar el log '{log_path}': {exc}", file=sys.stderr)
        return None
    return log_path


def _credenciales_admin() -> tuple[str, str]:
    email = os.environ.get("ADMIN_EMAIL", "")
    password = os.environ.get("ADMIN_PASSWORD", "")
    if not email or not password:
        raise ValueError(
            "Defina ADMIN_EMAIL y ADMIN_PASSWORD en el entorno o en .env para sembrar "
            "el administrador inicial."
        )
    return email, password


def _ejecutar_generar(root: Path, dry_run: bool) -> int:
    command = "generar"
    try:
        paths = generar_archivos(root, dry_run=dry_run)
    except (OSError, ValueError, TemplateError) as exc:
        log_path = _escribir_log(root, command, "ERROR", str(exc))
        print(f"ERROR: {exc}", file=sys.stderr)
        if log_path is not None:
            print(f"Log: {log_path}", file=sys.stderr)
        return 1

    action = "Se generarían" if dry_run else "Generado"
    message = f"{action}: " + ", ".join(str(path) for path in paths)
    log_path = _escribir_log(root, command, "OK", message)
    if log_path is None:
        return 1
    print(f"{message}. Log: {log_path}")
    return 0


def _ejecutar_bd(args: argparse.Namespace, root: Path) -> int:
    command = args.comando
    try:
        cargar_env_file(root / ".env")
        connection = parametros_conexion()
        admin_email, admin_password = _credenciales_admin()
        if command == "crear-bd":
            migrations = _migrations(root)
            if args.dry_run:
                message = (
                    f"Se validaron {len(migrations)} migración(es); no se conectó "
                    "ni se modificó la base de datos."
                )
            else:
                result = crear_base_y_sembrar(
                    root,
                    connection,
                    admin_email,
                    admin_password,
                )
                message = json.dumps(result, ensure_ascii=False, sort_keys=True)
        elif command == "sembrar":
            _migrations(root)
            if args.dry_run:
                message = "Se cargarían las semillas; no se conectó a la base."
            else:
                result = sembrar_base(
                    root,
                    connection,
                    admin_email,
                    admin_password,
                )
                message = json.dumps(result, ensure_ascii=False, sort_keys=True)
        else:
            _migrations(root)
            reiniciar_base_local(
                connection,
                args.confirm_db,
                dry_run=args.dry_run,
            )
            if args.dry_run:
                message = (
                    f"Se reiniciaría la base local '{connection['dbname']}'; "
                    "no se conectó ni se borraron datos."
                )
            else:
                result = crear_base_y_sembrar(
                    root,
                    connection,
                    admin_email,
                    admin_password,
                )
                message = f"Base local '{connection['dbname']}' recreada. " + json.dumps(
                    result, ensure_ascii=False, sort_keys=True
                )
    except (
        OSError,
        ValueError,
        RuntimeError,
        KeyError,
        TypeError,
        psycopg.Error,
    ) as exc:
        log_path = _escribir_log(root, command, "ERROR", str(exc))
        print(f"ERROR: {exc}", file=sys.stderr)
        if log_path is not None:
            print(f"Log: {log_path}", file=sys.stderr)
        return 1

    status = "DRY-RUN" if args.dry_run else "OK"
    log_path = _escribir_log(root, command, status, message)
    if log_path is None:
        return 1
    print(f"{message} Log: {log_path}")
    return 0


def _ejecutar_probar(args: argparse.Namespace, root: Path) -> int:
    suites = [suite.strip() for suite in args.suites.split(",") if suite.strip()]
    try:
        codigo, resumen = probar(root, suites, dry_run=args.dry_run, e2e_entorno=args.e2e_entorno)
    except ValueError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1
    if resumen is not None:
        estado = "Todas las suites pasaron" if codigo == 0 else "Hay suites con fallos"
        print(f"{estado}. Resumen: {resumen}")
    return codigo


def _ejecutar_todo(args: argparse.Namespace, root: Path) -> int:
    """validar → generar → crear-bd → probar; se detiene en el primer paso fallido."""
    pasos = [
        (
            "validar",
            lambda: _validar(
                argparse.Namespace(dry_run=args.dry_run, model_dir=None, schema=None, log_dir=None)
            ),
        ),
        ("generar", lambda: _ejecutar_generar(root, args.dry_run)),
        (
            "crear-bd",
            lambda: _ejecutar_bd(
                argparse.Namespace(comando="crear-bd", dry_run=args.dry_run), root
            ),
        ),
        ("probar", lambda: _ejecutar_probar(args, root)),
    ]
    for nombre, paso in pasos:
        print(f"== {nombre}", flush=True)
        codigo = paso()
        if codigo != 0:
            print(f"'todo' se detuvo en '{nombre}' (código {codigo}).", file=sys.stderr)
            return codigo
    return 0


def main(argv: list[str] | None = None) -> int:
    """Ejecuta los comandos disponibles e informa los que siguen pendientes."""
    parser = crear_parser()
    args = parser.parse_args(argv)
    root = Path(__file__).resolve().parents[1]
    if args.comando == "validar":
        return _validar(args)
    if args.comando == "generar":
        return _ejecutar_generar(root, args.dry_run)
    if args.comando in {"crear-bd", "reiniciar-bd", "sembrar"}:
        return _ejecutar_bd(args, root)
    if args.comando == "probar":
        return _ejecutar_probar(args, root)
    return _ejecutar_todo(args, root)


if __name__ == "__main__":
    raise SystemExit(main())
