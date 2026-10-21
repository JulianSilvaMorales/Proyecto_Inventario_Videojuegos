"""Respaldo y restauración de PostgreSQL con pg_dump/pg_restore (RNF-011).

Las herramientas de PostgreSQL se ejecutan dentro de la imagen oficial
postgres:16-alpine, la misma versión mayor que la base de Docker Compose y la de
Render (render.yaml). Así no hace falta instalarlas en el equipo y la versión de
pg_dump siempre es compatible con el servidor. La contraseña viaja como variable
de entorno del contenedor, nunca en la línea de comandos.
"""

from __future__ import annotations

import ipaddress
import os
import re
import subprocess
import sys
from collections.abc import Iterable, Sequence
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from pathlib import Path
from typing import Any

IMAGEN = "postgres:16-alpine"
DIRECTORIO_CONTENEDOR = "/respaldos"
EXTENSION = ".dump"
# Política de RNF-011: copias diarias 35 días y una copia mensual 12 meses.
DIAS_DIARIOS = 35
MESES_MENSUALES = 12
_PATRON_ARCHIVO = re.compile(r"-(\d{8}-\d{6})\.dump$")


class RespaldoError(RuntimeError):
    """pg_dump o pg_restore terminaron con error."""


@dataclass(frozen=True)
class Respaldo:
    ruta: Path
    fecha: datetime


def _es_local(host: str) -> bool:
    normalizado = host.strip().lower().rstrip(".")
    if normalizado == "localhost":
        return True
    try:
        return ipaddress.ip_address(normalizado).is_loopback
    except ValueError:
        return False


def _red_del_equipo() -> bool:
    """En Linux el contenedor usa la red del equipo; en Docker Desktop no existe esa
    opción y se llega al equipo por host.docker.internal."""
    return sys.platform.startswith("linux")


def variables_pg(parametros: dict[str, Any]) -> dict[str, str]:
    """Variables libpq (PGHOST, PGPASSWORD…) equivalentes a los parámetros de conexión."""
    equivalencias = {
        "host": "PGHOST",
        "port": "PGPORT",
        "dbname": "PGDATABASE",
        "user": "PGUSER",
        "password": "PGPASSWORD",  # pragma: allowlist secret
        "sslmode": "PGSSLMODE",
    }
    variables = {
        nombre: str(parametros[clave])
        for clave, nombre in equivalencias.items()
        if parametros.get(clave) not in (None, "")
    }
    # Desde el contenedor, 'localhost' es el propio contenedor y no el equipo.
    if "PGHOST" in variables and _es_local(variables["PGHOST"]) and not _red_del_equipo():
        variables["PGHOST"] = "host.docker.internal"
    return variables


def comando_docker(
    directorio: Path,
    variables: dict[str, str],
    herramienta: Sequence[str],
) -> list[str]:
    """`docker run` que monta el directorio de respaldos y hereda las variables PG*."""
    comando = ["docker", "run", "--rm"]
    if _red_del_equipo():
        # Con el usuario del equipo, el respaldo no queda a nombre de root.
        comando += ["--network=host", f"--user={os.getuid()}:{os.getgid()}"]
    comando += ["-v", f"{directorio.resolve()}:{DIRECTORIO_CONTENEDOR}"]
    for nombre in sorted(variables):
        # Solo el nombre: Docker toma el valor del entorno del proceso.
        comando += ["-e", nombre]
    return [*comando, IMAGEN, *herramienta]


def _ejecutar(comando: list[str], variables: dict[str, str]) -> None:
    resultado = subprocess.run(
        comando,
        env={**os.environ, **variables},
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=False,
    )
    if resultado.returncode != 0:
        detalle = (resultado.stderr or resultado.stdout).strip()
        herramienta = comando[comando.index(IMAGEN) + 1]
        raise RespaldoError(f"{herramienta} falló: {detalle}")


def nombre_respaldo(base: str, momento: datetime) -> str:
    return f"{base}-{momento.astimezone(UTC):%Y%m%d-%H%M%S}{EXTENSION}"


def respaldar(
    parametros: dict[str, Any],
    directorio: Path,
    momento: datetime | None = None,
) -> Path:
    """Genera un respaldo en formato personalizado de pg_dump (comprimido)."""
    directorio.mkdir(parents=True, exist_ok=True)
    archivo = directorio / nombre_respaldo(str(parametros["dbname"]), momento or datetime.now(UTC))
    variables = variables_pg(parametros)
    comando = comando_docker(
        directorio,
        variables,
        [
            "pg_dump",
            "--format=custom",
            "--no-owner",
            "--no-privileges",
            f"--file={DIRECTORIO_CONTENEDOR}/{archivo.name}",
        ],
    )
    _ejecutar(comando, variables)
    if not archivo.exists() or archivo.stat().st_size == 0:
        raise RespaldoError(f"pg_dump no generó {archivo}.")
    return archivo


def restaurar(parametros: dict[str, Any], archivo: Path) -> None:
    """Reemplaza el contenido de la base con el respaldo, en una sola transacción."""
    if not archivo.is_file():
        raise FileNotFoundError(f"No existe el respaldo {archivo}.")
    variables = variables_pg(parametros)
    comando = comando_docker(
        archivo.parent,
        variables,
        [
            "pg_restore",
            "--clean",
            "--if-exists",
            "--no-owner",
            "--no-privileges",
            "--single-transaction",
            "--exit-on-error",
            f"--dbname={variables.get('PGDATABASE', '')}",
            f"{DIRECTORIO_CONTENEDOR}/{archivo.name}",
        ],
    )
    _ejecutar(comando, variables)


def listar_respaldos(directorio: Path) -> list[Respaldo]:
    respaldos = []
    for ruta in directorio.glob(f"*{EXTENSION}") if directorio.exists() else []:
        coincidencia = _PATRON_ARCHIVO.search(ruta.name)
        if coincidencia:
            fecha = datetime.strptime(coincidencia.group(1), "%Y%m%d-%H%M%S").replace(tzinfo=UTC)
            respaldos.append(Respaldo(ruta, fecha))
    return sorted(respaldos, key=lambda respaldo: respaldo.fecha)


def respaldos_a_eliminar(respaldos: Iterable[Respaldo], ahora: datetime) -> list[Respaldo]:
    """Aplica RNF-011: se conservan todas las copias de los últimos 35 días y, de
    las anteriores, la primera de cada mes durante 12 meses."""
    limite_diario = ahora - timedelta(days=DIAS_DIARIOS)
    mes_actual = ahora.year * 12 + ahora.month - 1
    primeros_del_mes: dict[tuple[int, int], Respaldo] = {}
    for respaldo in sorted(respaldos, key=lambda item: item.fecha):
        primeros_del_mes.setdefault((respaldo.fecha.year, respaldo.fecha.month), respaldo)

    eliminar = []
    for respaldo in sorted(respaldos, key=lambda item: item.fecha):
        if respaldo.fecha >= limite_diario:
            continue
        meses = mes_actual - (respaldo.fecha.year * 12 + respaldo.fecha.month - 1)
        mensual = primeros_del_mes[(respaldo.fecha.year, respaldo.fecha.month)] is respaldo
        if mensual and meses < MESES_MENSUALES:
            continue
        eliminar.append(respaldo)
    return eliminar
