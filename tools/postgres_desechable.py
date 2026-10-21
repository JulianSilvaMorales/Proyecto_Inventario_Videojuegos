"""PostgreSQL 16 desechable en Docker para pruebas (pytest y E2E)."""

from __future__ import annotations

import secrets
import shutil
import subprocess
import time
from collections.abc import Iterator
from contextlib import contextmanager
from typing import Any
from uuid import uuid4

import psycopg

IMAGEN = "postgres:16-alpine"


def docker_disponible() -> bool:
    if shutil.which("docker") is None:
        return False
    try:
        resultado = subprocess.run(["docker", "info"], capture_output=True, timeout=60, check=False)
    except (OSError, subprocess.TimeoutExpired):
        return False
    return resultado.returncode == 0


def _esperar(parametros: dict[str, Any], segundos: int = 90) -> None:
    """La imagen reinicia el servidor tras inicializarse: se exigen conexiones estables."""
    seguidas = 0
    limite = time.monotonic() + segundos
    while time.monotonic() < limite:
        try:
            with psycopg.connect(**parametros, connect_timeout=2) as conexion:
                conexion.execute("SELECT 1")
            seguidas += 1
            if seguidas == 3:
                return
        except psycopg.OperationalError:
            seguidas = 0
        time.sleep(1)
    raise TimeoutError("El PostgreSQL de pruebas no quedó disponible a tiempo.")


@contextmanager
def postgres_temporal(base: str, prefijo: str = "inventario-pruebas") -> Iterator[dict[str, Any]]:
    """Inicia un contenedor solo en 127.0.0.1 y lo elimina al salir, incluso ante errores."""
    nombre = f"{prefijo}-{uuid4().hex[:8]}"
    clave = secrets.token_urlsafe(18)
    subprocess.run(
        [
            "docker",
            "run",
            "-d",
            "--rm",
            "--name",
            nombre,
            "-e",
            f"POSTGRES_PASSWORD={clave}",
            "-e",
            f"POSTGRES_DB={base}",
            "-p",
            "127.0.0.1::5432",
            IMAGEN,
        ],
        check=True,
        capture_output=True,
        timeout=600,
    )
    try:
        salida = subprocess.run(
            ["docker", "port", nombre, "5432/tcp"],
            check=True,
            capture_output=True,
            text=True,
            timeout=60,
        ).stdout
        puerto = int(salida.strip().splitlines()[0].rsplit(":", 1)[1])
        parametros = {
            "host": "127.0.0.1",
            "port": puerto,
            "dbname": base,
            "user": "postgres",
            "password": clave,
        }
        _esperar(parametros)
        yield parametros
    finally:
        subprocess.run(
            ["docker", "rm", "-f", nombre], capture_output=True, check=False, timeout=120
        )
