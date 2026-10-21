"""Pruebas de crear-bd contra un PostgreSQL 16 real y desechable (Docker).

Cubren los criterios de aceptación de E3: idempotencia, recreación local, saldo
nunca negativo e inmutabilidad de los movimientos. Sin Docker se omiten.
"""

from __future__ import annotations

import secrets
from collections.abc import Iterator
from datetime import UTC, datetime
from pathlib import Path
from typing import Any
from uuid import uuid4

import psycopg
import pytest

from tools.database import crear_base_y_sembrar, reiniciar_base_local
from tools.postgres_desechable import docker_disponible, postgres_temporal

ROOT = Path(__file__).resolve().parents[2]
ADMIN_EMAIL = "admin-pytest@inventario.invalid"
MIGRACIONES = ["V001", "V002", "V003"]


@pytest.fixture(scope="module")
def postgres() -> Iterator[dict[str, Any]]:
    if not docker_disponible():
        pytest.skip("Docker no está disponible.")
    with postgres_temporal("inventario_pytest", prefijo="inventario-pytest") as parametros:
        yield parametros


@pytest.fixture(scope="module")
def base_creada(postgres: dict[str, Any]) -> dict[str, Any]:
    return crear_base_y_sembrar(ROOT, postgres, ADMIN_EMAIL, secrets.token_urlsafe(18))


def _producto_y_bodega(conexion: psycopg.Connection) -> tuple[Any, Any, Any]:
    sufijo = uuid4().hex[:8]
    tipo, categoria, subcategoria, producto = uuid4(), uuid4(), uuid4(), uuid4()
    ahora = datetime.now(UTC)
    conexion.execute(
        "INSERT INTO public.tipo_producto (id, codigo, nombre, activo)"
        " VALUES (%s, %s, 'Tipo', TRUE)",
        (tipo, f"T-{sufijo}"),
    )
    conexion.execute(
        "INSERT INTO public.categoria (id, codigo, nombre, padre_id, activo)"
        " VALUES (%s, %s, 'Categoría', NULL, TRUE), (%s, %s, 'Subcategoría', %s, TRUE)",
        (categoria, f"C-{sufijo}", subcategoria, f"S-{sufijo}", categoria),
    )
    conexion.execute(
        "INSERT INTO public.producto (id, codigo_interno, tipo_producto_id, categoria_id,"
        " subcategoria_id, atributos, activo, creado_en_utc, actualizado_en_utc)"
        " VALUES (%s, %s, %s, %s, %s, '{}'::jsonb, TRUE, %s, %s)",
        (producto, f"PRD-{sufijo}", tipo, categoria, subcategoria, ahora, ahora),
    )
    bodega = conexion.execute("SELECT id FROM public.bodega WHERE codigo = 'GENERAL'").fetchone()[0]
    usuario = conexion.execute(
        "SELECT id FROM public.usuario WHERE correo = %s", (ADMIN_EMAIL,)
    ).fetchone()[0]
    return producto, bodega, usuario


def test_crear_bd_aplica_todo_y_es_idempotente(
    postgres: dict[str, Any], base_creada: dict[str, Any]
) -> None:
    assert base_creada["migraciones_aplicadas"] == MIGRACIONES
    assert base_creada["verificacion"]["migraciones"] == len(MIGRACIONES)

    segunda = crear_base_y_sembrar(ROOT, postgres, ADMIN_EMAIL, secrets.token_urlsafe(18))

    assert segunda["migraciones_aplicadas"] == []
    assert set(segunda["semillas_insertadas"].values()) == {0}
    assert segunda["verificacion"] == base_creada["verificacion"]


def test_saldo_negativo_se_rechaza(postgres: dict[str, Any], base_creada: dict[str, Any]) -> None:
    with psycopg.connect(**postgres) as conexion:
        producto, bodega, _ = _producto_y_bodega(conexion)
        with pytest.raises(psycopg.errors.CheckViolation):
            conexion.execute(
                "INSERT INTO public.saldo_inventario (id, producto_id, bodega_id, cantidad,"
                " actualizado_en_utc) VALUES (%s, %s, %s, -1, now())",
                (uuid4(), producto, bodega),
            )


@pytest.mark.parametrize(
    "sentencia",
    [
        "UPDATE public.movimiento_inventario SET cantidad = 99 WHERE id = %s",
        "DELETE FROM public.movimiento_inventario WHERE id = %s",
    ],
)
def test_movimientos_son_inmutables(
    postgres: dict[str, Any], base_creada: dict[str, Any], sentencia: str
) -> None:
    with psycopg.connect(**postgres) as conexion:
        producto, bodega, usuario = _producto_y_bodega(conexion)
        movimiento = uuid4()
        conexion.execute(
            "INSERT INTO public.movimiento_inventario (id, producto_id, bodega_id, tipo, cantidad,"
            " referencia_tipo, referencia_id, usuario_id, fecha_utc)"
            " VALUES (%s, %s, %s, 'ENTRADA', 1, 'PRUEBA', %s, %s, now())",
            (movimiento, producto, bodega, uuid4(), usuario),
        )
        conexion.commit()
        with pytest.raises(psycopg.errors.ObjectNotInPrerequisiteState, match="inmutable"):
            conexion.execute(sentencia, (movimiento,))


def test_reiniciar_bd_recrea_la_base_local(
    postgres: dict[str, Any], base_creada: dict[str, Any]
) -> None:
    reiniciar_base_local(postgres, postgres["dbname"])
    recreada = crear_base_y_sembrar(ROOT, postgres, ADMIN_EMAIL, secrets.token_urlsafe(18))

    assert recreada["migraciones_aplicadas"] == MIGRACIONES
    with psycopg.connect(**postgres) as conexion:
        assert conexion.execute("SELECT count(*) FROM public.producto").fetchone()[0] == 0
