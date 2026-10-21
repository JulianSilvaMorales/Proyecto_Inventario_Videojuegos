"""Creación local, migraciones, semillas y verificaciones de PostgreSQL."""

from __future__ import annotations

import ipaddress
import os
import re
from collections.abc import Iterator
from contextlib import contextmanager
from hashlib import sha256
from pathlib import Path
from typing import Any
from uuid import uuid4

import psycopg
from psycopg import sql
from psycopg.conninfo import conninfo_to_dict

if __package__:
    from .model_validator import validar_modelo
    from .seed_loader import sembrar_datos
    from .sql_generator import GENERATED_HEADER, _constraint_name, cargar_modulos
else:
    from model_validator import validar_modelo
    from seed_loader import sembrar_datos
    from sql_generator import GENERATED_HEADER, _constraint_name, cargar_modulos


LEGACY_V001_CHECKSUM = (
    "fe2b344f8934e2dc0e3d8cfa07b2db3e"  # pragma: allowlist secret
    + "719ce0e031a80a5baa62bb51ca652347"  # pragma: allowlist secret
)
MIGRATION_CHECKSUM_COMPATIBILITY = {"V001": frozenset({LEGACY_V001_CHECKSUM})}


class MigrationChecksumError(RuntimeError):
    """Una migración aplicada cambió después de quedar registrada."""


class DatabaseVerificationError(RuntimeError):
    """El esquema o los datos iniciales no cumplen las verificaciones."""


def parametros_conexion(environment: dict[str, str] | None = None) -> dict[str, Any]:
    environment = environment or os.environ
    database_url = environment.get("DATABASE_URL", "").strip()
    if database_url:
        parameters = conninfo_to_dict(database_url)
        if not parameters.get("dbname"):
            raise ValueError("DATABASE_URL debe especificar una base de datos.")
        return parameters

    required = ("DB_HOST", "DB_PORT", "DB_NAME", "DB_USER")
    missing = [key for key in required if not environment.get(key)]
    if missing:
        raise ValueError("Faltan variables de conexión a PostgreSQL: " + ", ".join(missing) + ".")
    try:
        port = int(environment["DB_PORT"])
    except ValueError as exc:
        raise ValueError("DB_PORT debe ser un número entero.") from exc
    if not 1 <= port <= 65535:
        raise ValueError("DB_PORT debe estar entre 1 y 65535.")

    parameters: dict[str, Any] = {
        "host": environment["DB_HOST"],
        "port": port,
        "dbname": environment["DB_NAME"],
        "user": environment["DB_USER"],
    }
    if environment.get("DB_PASSWORD"):
        parameters["password"] = environment["DB_PASSWORD"]
    return parameters


def _nombre_base(parameters: dict[str, Any]) -> str:
    name = parameters.get("dbname")
    if not isinstance(name, str) or not name:
        raise ValueError("No se pudo determinar el nombre de la base de datos.")
    return name


def crear_base_si_no_existe(parameters: dict[str, Any]) -> bool:
    """Crea la base si falta y devuelve si se creó en esta ejecución.

    Primero intenta conectarse a la base de destino: en servicios gestionados como
    Render la base ya existe y el usuario no puede entrar a 'postgres' ni crear bases.
    """
    database_name = _nombre_base(parameters)
    try:
        with psycopg.connect(**parameters, connect_timeout=15):
            return False
    except psycopg.OperationalError:
        pass
    admin_parameters = dict(parameters, dbname="postgres")
    with psycopg.connect(**admin_parameters, autocommit=True) as connection:
        exists = connection.execute(
            "SELECT 1 FROM pg_database WHERE datname = %s",
            (database_name,),
        ).fetchone()
        if exists:
            return False
        connection.execute(sql.SQL("CREATE DATABASE {}").format(sql.Identifier(database_name)))
    return True


def _es_host_local(parameters: dict[str, Any]) -> bool:
    host = parameters.get("host")
    if not isinstance(host, str) or not host.strip():
        return False
    normalized_host = host.strip().lower()
    if normalized_host in {"localhost", "localhost."}:
        return True
    try:
        return ipaddress.ip_address(normalized_host).is_loopback
    except ValueError:
        return False


def reiniciar_base_local(
    parameters: dict[str, Any],
    confirm_database: str | None,
    dry_run: bool = False,
) -> None:
    """Elimina y recrea solo una base local cuyo nombre se confirmó explícitamente."""
    database_name = _nombre_base(parameters)
    if not _es_host_local(parameters):
        raise ValueError(
            "Se bloqueó el reinicio: solo se permite contra localhost o una IP de loopback."
        )
    if database_name in {"postgres", "template0", "template1"}:
        raise ValueError(f"Se bloqueó el reinicio de la base protegida '{database_name}'.")
    if confirm_database != database_name:
        raise ValueError(
            "Confirme explícitamente la base con --confirm-db "
            f"{database_name} antes de reiniciarla."
        )
    if dry_run:
        return

    admin_parameters = dict(parameters, dbname="postgres")
    with psycopg.connect(**admin_parameters, autocommit=True) as connection:
        connection.execute(
            "SELECT pg_terminate_backend(pid) FROM pg_stat_activity "
            "WHERE datname = %s AND pid <> pg_backend_pid()",
            (database_name,),
        )
        connection.execute(
            sql.SQL("DROP DATABASE IF EXISTS {}").format(sql.Identifier(database_name))
        )
        connection.execute(sql.SQL("CREATE DATABASE {}").format(sql.Identifier(database_name)))


def _migrations(root: Path) -> list[Path]:
    migrations = sorted((root / "db" / "migrations").glob("V*.sql"))
    if not migrations:
        raise ValueError(
            "No hay migraciones generadas. Ejecute primero 'python tools/cli.py generar'."
        )
    for migration in migrations:
        if not re.match(r"^V[0-9]{3,}__[a-z0-9_]+\.sql$", migration.name):
            raise ValueError(f"Nombre de migración inválido: '{migration.name}'.")
    return migrations


def aplicar_migraciones(
    connection: psycopg.Connection,
    migrations: list[Path],
) -> list[str]:
    applied_versions: list[str] = []
    for migration in migrations:
        content = migration.read_text(encoding="utf-8")
        if not content.splitlines() or GENERATED_HEADER not in content.splitlines()[0]:
            raise ValueError(
                f"Se rehúsa aplicar '{migration}': falta la cabecera de archivo generado."
            )
        version = migration.stem.split("__", maxsplit=1)[0]
        checksum = sha256(content.encode("utf-8")).hexdigest()

        with connection.transaction():
            migration_table = connection.execute(
                "SELECT to_regclass('public.migracion_historial')"
            ).fetchone()[0]
            applied = None
            if migration_table is not None:
                applied = connection.execute(
                    "SELECT checksum FROM public.migracion_historial WHERE version = %s",
                    (version,),
                ).fetchone()
            if applied is not None:
                if applied[0] != checksum:
                    if applied[0] in MIGRATION_CHECKSUM_COMPATIBILITY.get(version, frozenset()):
                        continue
                    raise MigrationChecksumError(
                        f"El checksum de {version} no coincide con el registrado; "
                        "no se aplica una migración modificada."
                    )
                continue

            connection.execute(content)
            connection.execute(
                "INSERT INTO public.migracion_historial "
                "(id, version, checksum, aplicada_en_utc) VALUES (%s, %s, %s, now())",
                (uuid4(), version, checksum),
            )
            applied_versions.append(version)
    return applied_versions


@contextmanager
def conexion_base(
    parameters: dict[str, Any],
    create_if_missing: bool = True,
) -> Iterator[psycopg.Connection]:
    if create_if_missing:
        crear_base_si_no_existe(parameters)
    with psycopg.connect(**parameters) as connection:
        yield connection


def verificar_base(connection: psycopg.Connection, root: Path) -> dict[str, int]:
    model_dir = root / "db" / "model" / "modulos"
    errors, expected_count = validar_modelo(
        model_dir,
        root / "db" / "model" / "model.schema.json",
    )
    if errors:
        raise DatabaseVerificationError(
            "El modelo no es válido durante la verificación:\n" + "\n".join(errors)
        )

    expected_entities = {
        entity["nombre"] for module in cargar_modulos(model_dir) for entity in module["entidades"]
    }
    actual_entities = {
        row[0]
        for row in connection.execute(
            "SELECT table_name FROM information_schema.tables "
            "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'"
        ).fetchall()
    }
    missing_entities = expected_entities - actual_entities
    if missing_entities:
        raise DatabaseVerificationError(
            "Faltan tablas en PostgreSQL: " + ", ".join(sorted(missing_entities)) + "."
        )

    check_definitions = connection.execute(
        "SELECT pg_get_constraintdef(constraint_row.oid) "
        "FROM pg_constraint AS constraint_row "
        "JOIN pg_class AS table_row ON table_row.oid = constraint_row.conrelid "
        "WHERE table_row.relname = 'saldo_inventario' "
        "AND constraint_row.contype = 'c'"
    ).fetchall()
    if not any("cantidad >= 0" in str(row[0]).lower() for row in check_definitions):
        raise DatabaseVerificationError(
            "No se encontró CHECK de cantidad no negativa en saldo_inventario."
        )

    immutable_entities = ["movimiento_inventario", "auditoria"]
    expected_triggers = {
        _constraint_name(["trg", entity, "inmutable"]) for entity in immutable_entities
    }
    expected_triggers.add(_constraint_name(["trg", "producto", "sin_delete"]))
    actual_triggers = {
        row[0]
        for row in connection.execute(
            "SELECT trigger_row.tgname FROM pg_trigger AS trigger_row "
            "JOIN pg_class AS table_row ON table_row.oid = trigger_row.tgrelid "
            "JOIN pg_namespace AS namespace_row ON namespace_row.oid = table_row.relnamespace "
            "WHERE namespace_row.nspname = 'public' AND NOT trigger_row.tgisinternal"
        ).fetchall()
    }
    missing_triggers = expected_triggers - actual_triggers
    if missing_triggers:
        raise DatabaseVerificationError(
            "Faltan triggers de inmutabilidad: " + ", ".join(sorted(missing_triggers)) + "."
        )

    migration_count = connection.execute(
        "SELECT count(*) FROM public.migracion_historial"
    ).fetchone()[0]
    if migration_count < 1:
        raise DatabaseVerificationError("No hay migraciones registradas.")

    seed_tables = ("pais", "moneda", "idioma", "rol", "permiso", "parametro", "bodega")
    counts: dict[str, int] = {"entidades": expected_count, "migraciones": migration_count}
    for table in seed_tables:
        count = connection.execute(
            sql.SQL("SELECT count(*) FROM public.{}").format(sql.Identifier(table))
        ).fetchone()[0]
        if count < 1:
            raise DatabaseVerificationError(f"La tabla de semillas '{table}' está vacía.")
        counts[table] = count

    countries = {
        row[0]: row[1]
        for row in connection.execute(
            "SELECT codigo_iso, habilitado FROM public.pais WHERE codigo_iso IN ('CO', 'US')"
        ).fetchall()
    }
    if countries.get("CO") is not True:
        raise DatabaseVerificationError("La configuración inicial requiere Colombia habilitada.")
    tax_count = connection.execute(
        "SELECT count(*) FROM public.impuesto WHERE pais_id = "
        "(SELECT id FROM public.pais WHERE codigo_iso = 'CO')"
    ).fetchone()[0]
    if tax_count < 1:
        raise DatabaseVerificationError("No se encontró la tarifa inicial de Colombia.")
    us_tax_count = connection.execute(
        "SELECT count(*) FROM public.impuesto WHERE pais_id = "
        "(SELECT id FROM public.pais WHERE codigo_iso = 'US')"
    ).fetchone()[0]
    if countries.get("US") is True and us_tax_count < 1:
        raise DatabaseVerificationError(
            "Estados Unidos no puede habilitarse sin al menos una tarifa de impuesto."
        )
    counts["impuesto_co"] = tax_count
    counts["impuesto_us"] = us_tax_count
    return counts


def crear_base_y_sembrar(
    root: Path,
    parameters: dict[str, Any],
    admin_email: str,
    admin_password: str,
) -> dict[str, Any]:
    migrations = _migrations(root)
    seed_path = root / "db" / "seeds" / "inicial.json"
    model_errors, _ = validar_modelo(
        root / "db" / "model" / "modulos",
        root / "db" / "model" / "model.schema.json",
    )
    if model_errors:
        raise DatabaseVerificationError(
            "No se crea la base con un modelo inválido:\n" + "\n".join(model_errors)
        )

    with conexion_base(parameters) as connection:
        applied = aplicar_migraciones(connection, migrations)
        with connection.transaction():
            seed_counts = sembrar_datos(connection, seed_path, admin_email, admin_password)
        verified = verificar_base(connection, root)
    return {
        "migraciones_aplicadas": applied,
        "semillas_insertadas": seed_counts,
        "verificacion": verified,
    }


def sembrar_base(
    root: Path,
    parameters: dict[str, Any],
    admin_email: str,
    admin_password: str,
) -> dict[str, int]:
    _migrations(root)
    seed_path = root / "db" / "seeds" / "inicial.json"
    with conexion_base(parameters, create_if_missing=False) as connection:
        with connection.transaction():
            counts = sembrar_datos(connection, seed_path, admin_email, admin_password)
        verificar_base(connection, root)
    return counts
