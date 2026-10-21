"""Pruebas de seguridad y configuración para operaciones PostgreSQL."""

from __future__ import annotations

import json
import os
import subprocess
import sys
from contextlib import nullcontext
from hashlib import sha256
from pathlib import Path
from unittest.mock import patch

import psycopg
import pytest

from tools.database import (
    LEGACY_V001_CHECKSUM,
    MigrationChecksumError,
    aplicar_migraciones,
    crear_base_si_no_existe,
    parametros_conexion,
    reiniciar_base_local,
)
from tools.seed_loader import _load_seed_data, sembrar_datos
from tools.sql_generator import GENERATED_HEADER

ROOT = Path(__file__).resolve().parents[2]


class Cursor:
    def __init__(self, row: tuple | None = None):
        self.row = row
        self.rowcount = 1

    def fetchone(self) -> tuple | None:
        return self.row


class FakeConnection:
    def __init__(self, cursor: Cursor):
        self.cursor = cursor

    def __enter__(self):
        return self

    def __exit__(self, *_args):
        return None

    def execute(self, _statement, _parameters=None):
        return self.cursor


class MigrationConnection:
    def __init__(self, checksum: str | None = None):
        self.checksum = checksum
        self.history_exists = checksum is not None
        self.applied_sql: list[str] = []
        self.recorded: list[tuple] = []

    def transaction(self):
        return nullcontext()

    def execute(self, statement, parameters=None):
        if statement == "SELECT to_regclass('public.migracion_historial')":
            return Cursor(("migracion_historial",) if self.history_exists else (None,))
        if statement.startswith("SELECT checksum FROM public.migracion_historial"):
            return Cursor((self.checksum,) if self.checksum else None)
        if statement.startswith("INSERT INTO public.migracion_historial"):
            self.recorded.append(parameters)
            self.checksum = parameters[2]
            self.history_exists = True
            return Cursor()
        self.applied_sql.append(statement)
        return Cursor()


class SeedConnection:
    def __init__(self):
        self.inserts: list[tuple[object, list[object]]] = []

    def execute(self, statement, parameters):
        self.inserts.append((statement, parameters))
        return Cursor()


def test_conexion_acepta_database_url() -> None:
    parameters = parametros_conexion({"DATABASE_URL": "postgresql://localhost:5432/inventory"})

    assert parameters["dbname"] == "inventory"
    assert parameters["host"] == "localhost"
    assert parameters["port"] == "5432"


def test_conexion_rechaza_configuracion_incompleta() -> None:
    with pytest.raises(ValueError, match="Faltan variables"):
        parametros_conexion({"DB_HOST": "localhost"})


def test_conexion_rechaza_puerto_invalido() -> None:
    with pytest.raises(ValueError, match="DB_PORT"):
        parametros_conexion(
            {
                "DB_HOST": "localhost",
                "DB_PORT": "no-es-puerto",
                "DB_NAME": "inventario",
                "DB_USER": "inventario",
            }
        )


def test_reinicio_remoto_se_bloquea_sin_conectar() -> None:
    parameters = {"host": "db.example.net", "dbname": "inventario"}
    with patch("tools.database.psycopg.connect") as connect:
        with pytest.raises(ValueError, match="solo se permite"):
            reiniciar_base_local(parameters, confirm_database="inventario")
    connect.assert_not_called()


def test_reinicio_local_exige_confirmar_nombre_exacto_sin_conectar() -> None:
    parameters = {"host": "127.0.0.1", "dbname": "inventario"}
    with patch("tools.database.psycopg.connect") as connect:
        with pytest.raises(ValueError, match="--confirm-db"):
            reiniciar_base_local(parameters, confirm_database="otra")
    connect.assert_not_called()


def test_reinicio_dry_run_local_no_conecta() -> None:
    parameters = {"host": "localhost", "dbname": "inventario"}
    with patch("tools.database.psycopg.connect") as connect:
        reiniciar_base_local(parameters, "inventario", dry_run=True)
    connect.assert_not_called()


def test_creacion_de_base_existente_no_entra_a_postgres() -> None:
    # En Render la base ya existe y el usuario no puede entrar a 'postgres'.
    connection = FakeConnection(Cursor((1,)))
    with patch("tools.database.psycopg.connect", return_value=connection) as connect:
        created = crear_base_si_no_existe(
            {"host": "render.example", "dbname": "inventario", "user": "test"}
        )

    assert created is False
    assert connect.call_count == 1
    assert connect.call_args.kwargs["dbname"] == "inventario"


def test_creacion_de_base_faltante_usa_postgres() -> None:
    connection = FakeConnection(Cursor(None))
    with patch(
        "tools.database.psycopg.connect",
        side_effect=[psycopg.OperationalError("no existe"), connection],
    ) as connect:
        created = crear_base_si_no_existe(
            {"host": "localhost", "dbname": "inventario", "user": "test"}
        )

    assert created is True
    assert connect.call_args.kwargs["dbname"] == "postgres"
    assert connect.call_args.kwargs["autocommit"] is True


def test_migracion_se_aplica_una_vez_y_se_registra_checksum(tmp_path: Path) -> None:
    content = f"-- {GENERATED_HEADER}\nCREATE TABLE prueba (id UUID PRIMARY KEY);\n"
    migration = tmp_path / "V001__fixture.sql"
    migration.write_text(content, encoding="utf-8")
    connection = MigrationConnection()

    first = aplicar_migraciones(connection, [migration])
    second = aplicar_migraciones(connection, [migration])

    assert first == ["V001"]
    assert second == []
    assert connection.applied_sql == [content]
    assert connection.recorded[0][2] == sha256(content.encode("utf-8")).hexdigest()


def test_migracion_modificada_rechaza_checksum_distinto(tmp_path: Path) -> None:
    migration = tmp_path / "V001__fixture.sql"
    migration.write_text(f"-- {GENERATED_HEADER}\nSELECT 1;\n", encoding="utf-8")
    connection = MigrationConnection(checksum="checksum-antiguo")

    with pytest.raises(MigrationChecksumError, match="no coincide"):
        aplicar_migraciones(connection, [migration])


def test_migracion_inicial_acepta_checksum_previo_publicado(tmp_path: Path) -> None:
    migration = tmp_path / "V001__modelo_inicial.sql"
    migration.write_text(f"-- {GENERATED_HEADER}\nSELECT 1;\n", encoding="utf-8")
    connection = MigrationConnection(checksum=LEGACY_V001_CHECKSUM)

    applied = aplicar_migraciones(connection, [migration])

    assert applied == []
    assert connection.applied_sql == []


def test_semillas_reflejan_configuracion_acordada() -> None:
    data = _load_seed_data(ROOT / "db" / "seeds" / "inicial.json")
    countries = {country["codigo_iso"]: country for country in data["paises"]}
    taxes = {tax["pais_codigo"]: tax for tax in data["impuestos"]}

    assert countries["CO"]["habilitado"] is True
    assert countries["US"]["habilitado"] is False
    assert taxes["CO"]["tasa"] == 0.19
    assert "US" not in taxes
    assert "tipos_cambio" not in data


def test_semillas_rechazan_campos_faltantes_con_error_legible(tmp_path: Path) -> None:
    seed_path = tmp_path / "inicial.json"
    data = _load_seed_data(ROOT / "db" / "seeds" / "inicial.json")
    data["impuestos"][0].pop("tasa")
    seed_path.write_text(json.dumps(data), encoding="utf-8")

    with pytest.raises(ValueError, match="campos requeridos"):
        _load_seed_data(seed_path)


def test_semillas_rechazan_bodega_con_pais_inexistente(tmp_path: Path) -> None:
    seed_path = tmp_path / "inicial.json"
    data = _load_seed_data(ROOT / "db" / "seeds" / "inicial.json")
    data["bodegas"][0]["pais_codigo"] = "ZZ"
    seed_path.write_text(json.dumps(data), encoding="utf-8")

    with pytest.raises(ValueError, match="bodega 'GENERAL' referencia un país inexistente"):
        _load_seed_data(seed_path)


def test_semillas_admiten_parametro_por_pais_y_rechazan_duplicado_de_ambito(
    tmp_path: Path,
) -> None:
    data = _load_seed_data(ROOT / "db" / "seeds" / "inicial.json")
    medios = [p for p in data["parametros"] if p["clave"] == "ventas.medios_pago"]
    assert {p.get("pais_codigo") for p in medios} == {None, "CO"}

    seed_path = tmp_path / "inicial.json"
    data["parametros"].append(dict(medios[1]))
    seed_path.write_text(json.dumps(data), encoding="utf-8")
    with pytest.raises(ValueError, match="duplicado para su ámbito"):
        _load_seed_data(seed_path)


def test_sembrar_almacena_contrasena_administrativa_con_hash_bcrypt() -> None:
    connection = SeedConnection()

    counts = sembrar_datos(
        connection,
        ROOT / "db" / "seeds" / "inicial.json",
        "ADMIN@example.test",
        "password-de-prueba",
    )

    user_insert = next(
        parameters
        for _statement, parameters in connection.inserts
        if len(parameters) > 3 and parameters[1] == "admin@example.test"
    )
    assert counts["moneda"] == 2
    assert counts["pais"] == 2
    assert user_insert[3].startswith("$2b$12$")
    assert user_insert[3] != "password-de-prueba"


def test_cli_crear_bd_dry_run_no_conecta_a_postgresql() -> None:
    environment = os.environ.copy()
    environment.pop("DATABASE_URL", None)
    environment.update(
        {
            "DB_HOST": "localhost",
            "DB_PORT": "5432",
            "DB_NAME": "inventario_prueba",
            "DB_USER": "inventario",
            "ADMIN_EMAIL": "admin@example.test",
            "ADMIN_PASSWORD": "password-de-prueba",  # pragma: allowlist secret
        }
    )
    result = subprocess.run(
        [
            sys.executable,
            str(ROOT / "tools" / "cli.py"),
            "crear-bd",
            "--dry-run",
        ],
        cwd=ROOT,
        env={**environment, "PYTHONIOENCODING": "utf-8"},
        capture_output=True,
        encoding="utf-8",
        check=False,
    )

    assert result.returncode == 0, result.stderr
    assert "no se conectó" in result.stdout
