"""Respaldo y restauración (RNF-011): política de retención, comandos y prueba real."""

from __future__ import annotations

from collections.abc import Iterator
from datetime import UTC, datetime, timedelta
from pathlib import Path
from typing import Any
from unittest.mock import patch
from uuid import uuid4

import psycopg
import pytest

from tools import respaldo
from tools.cli import main
from tools.database import crear_base_y_sembrar
from tools.postgres_desechable import docker_disponible, postgres_temporal
from tools.respaldo import (
    Respaldo,
    comando_docker,
    listar_respaldos,
    nombre_respaldo,
    respaldar,
    respaldos_a_eliminar,
    restaurar,
    variables_pg,
)

ROOT = Path(__file__).resolve().parents[2]
AHORA = datetime(2026, 10, 21, 12, 0, tzinfo=UTC)


def _respaldo(dias: int, hora: int = 2) -> Respaldo:
    fecha = (AHORA - timedelta(days=dias)).replace(hour=hora)
    return Respaldo(Path(nombre_respaldo("inventario", fecha)), fecha)


def test_retencion_conserva_35_dias_y_una_copia_mensual_por_12_meses() -> None:
    recientes = [_respaldo(dias) for dias in range(0, 35)]
    # Más de 35 días: 3 copias de julio de 2026 y 1 de octubre de 2025 (12 meses atrás).
    julio = [_respaldo(dias) for dias in (110, 100, 90)]
    vencida = _respaldo(385)

    eliminar = respaldos_a_eliminar([*recientes, *julio, vencida], AHORA)

    assert julio[0] not in eliminar  # primera de julio: copia mensual
    assert set(eliminar) == {julio[1], julio[2], vencida}


def test_listar_respaldos_lee_la_fecha_del_nombre(tmp_path: Path) -> None:
    (tmp_path / "inventario-20261020-020000.dump").write_bytes(b"x")
    (tmp_path / "inventario-20261021-020000.dump").write_bytes(b"x")
    (tmp_path / "notas.txt").write_text("no es un respaldo", encoding="utf-8")

    fechas = [item.fecha.day for item in listar_respaldos(tmp_path)]

    assert fechas == [20, 21]


def test_la_contrasena_no_aparece_en_el_comando(tmp_path: Path) -> None:
    variables = variables_pg(
        {
            "host": "db.render.example",
            "port": 5432,
            "dbname": "inv",
            "user": "u",
            "password": "s3",  # pragma: allowlist secret
        }
    )
    comando = comando_docker(tmp_path, variables, ["pg_dump"])

    assert variables["PGPASSWORD"] == "s3"  # pragma: allowlist secret
    assert "s3" not in " ".join(comando)
    assert comando[comando.index("PGPASSWORD") - 1] == "-e"
    assert comando[-2:] == [respaldo.IMAGEN, "pg_dump"]


def test_localhost_se_traduce_para_el_contenedor() -> None:
    with patch.object(respaldo, "_red_del_equipo", return_value=False):
        assert variables_pg({"host": "localhost"})["PGHOST"] == "host.docker.internal"
        assert variables_pg({"host": "127.0.0.1"})["PGHOST"] == "host.docker.internal"
        assert variables_pg({"host": "db.render.example"})["PGHOST"] == "db.render.example"
    with patch.object(respaldo, "_red_del_equipo", return_value=True):
        assert variables_pg({"host": "127.0.0.1"})["PGHOST"] == "127.0.0.1"


def test_restaurar_exige_confirmar_el_nombre_de_la_base(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    archivo = tmp_path / "inventario-20261021-020000.dump"
    archivo.write_bytes(b"x")
    monkeypatch.setenv("DATABASE_URL", "postgresql://u@db.render.example/inventario")

    with patch("tools.cli.restaurar") as restaurar_mock:
        codigo = main(["restaurar", "--archivo", str(archivo), "--confirm-db", "otra"])

    assert codigo == 1
    restaurar_mock.assert_not_called()
    assert "--confirm-db inventario" in capsys.readouterr().err


def test_respaldar_dry_run_no_ejecuta_docker(
    monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    monkeypatch.setenv("DATABASE_URL", "postgresql://u@db.render.example/inventario")

    with patch("tools.respaldo.subprocess.run") as run:
        assert main(["respaldar", "--dry-run"]) == 0

    run.assert_not_called()
    assert "no se conectó" in capsys.readouterr().out


@pytest.fixture(scope="module")
def postgres() -> Iterator[dict[str, Any]]:
    if not docker_disponible():
        pytest.skip("Docker no está disponible.")
    with postgres_temporal("inventario_respaldo", prefijo="inventario-respaldo") as parametros:
        crear_base_y_sembrar(ROOT, parametros, "admin-respaldo@inventario.invalid", "x" * 16)
        yield parametros


def _paises(parametros: dict[str, Any]) -> int:
    with psycopg.connect(**parametros) as conexion:
        return conexion.execute("SELECT count(*) FROM public.pais").fetchone()[0]


def test_respaldo_y_restauracion_reales(postgres: dict[str, Any], tmp_path: Path) -> None:
    paises = _paises(postgres)
    archivo = respaldar(postgres, tmp_path)
    assert archivo.stat().st_size > 0

    # Se simula la pérdida de datos: un país nuevo que no está en el respaldo.
    with psycopg.connect(**postgres) as conexion:
        conexion.execute(
            "INSERT INTO public.pais (id, codigo_iso, nombre, habilitado)"
            " VALUES (%s, 'ZZ', 'País de prueba', FALSE)",
            (uuid4(),),
        )
    assert _paises(postgres) == paises + 1

    restaurar(postgres, archivo)

    assert _paises(postgres) == paises
