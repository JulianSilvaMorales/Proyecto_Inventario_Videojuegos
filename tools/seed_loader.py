"""Carga idempotente de los datos iniciales del proyecto."""

from __future__ import annotations

import json
import math
from datetime import UTC, date, datetime
from decimal import Decimal
from pathlib import Path
from typing import Any
from uuid import NAMESPACE_URL, UUID, uuid5

import bcrypt
import psycopg
from psycopg import sql
from psycopg.types.json import Jsonb


def _id(table: str, natural_key: str) -> UUID:
    return uuid5(NAMESPACE_URL, f"proyecto-inventario:{table}:{natural_key}")


def _insert(
    connection: psycopg.Connection,
    table: str,
    record: dict[str, Any],
    json_fields: set[str] | None = None,
) -> bool:
    json_fields = json_fields or set()
    columns = list(record)
    values = [Jsonb(value) if column in json_fields else value for column, value in record.items()]
    statement = sql.SQL("INSERT INTO public.{} ({}) VALUES ({}) ON CONFLICT DO NOTHING").format(
        sql.Identifier(table),
        sql.SQL(", ").join(sql.Identifier(column) for column in columns),
        sql.SQL(", ").join(sql.Placeholder() for _ in columns),
    )
    cursor = connection.execute(statement, values)
    return cursor.rowcount == 1


def _load_seed_data(seed_path: Path) -> dict[str, Any]:
    try:
        with seed_path.open(encoding="utf-8") as seed_file:
            data = json.load(seed_file)
    except OSError as exc:
        raise ValueError(f"No se pudo leer el archivo de semillas '{seed_path}': {exc}") from exc
    except json.JSONDecodeError as exc:
        raise ValueError(
            f"JSON inválido en '{seed_path}', línea {exc.lineno}, columna {exc.colno}: {exc.msg}."
        ) from exc
    if not isinstance(data, dict):
        raise ValueError(f"El archivo de semillas '{seed_path}' debe contener un objeto JSON.")
    _validar_semillas(data, seed_path)
    return data


def _validar_semillas(data: dict[str, Any], seed_path: Path) -> None:
    required_lists = (
        "monedas",
        "idiomas",
        "paises",
        "impuestos",
        "bodegas",
        "roles",
        "permisos",
        "parametros",
    )
    missing = [key for key in required_lists if key not in data]
    if missing:
        raise ValueError(f"Faltan colecciones en '{seed_path}': {', '.join(missing)}.")
    for key in required_lists:
        if not isinstance(data[key], list):
            raise ValueError(f"La colección '{key}' de '{seed_path}' debe ser una lista.")
        if any(not isinstance(record, dict) for record in data[key]):
            raise ValueError(f"Cada elemento de '{key}' debe ser un objeto JSON.")
    if not isinstance(data.get("permisos_por_rol"), dict):
        raise ValueError(f"'permisos_por_rol' de '{seed_path}' debe ser un objeto.")

    identities = (
        ("monedas", "codigo_iso"),
        ("idiomas", "codigo_bcp47"),
        ("paises", "codigo_iso"),
        ("roles", "codigo"),
        ("permisos", "codigo"),
        ("bodegas", "codigo"),
    )
    for key, identity in identities:
        records = data[key]
        for record in records:
            if (
                not isinstance(record, dict)
                or not isinstance(record.get(identity), str)
                or not record[identity].strip()
            ):
                raise ValueError(f"Cada elemento de '{key}' debe incluir '{identity}'.")
        values = [record[identity] for record in records]
        if len(values) != len(set(values)):
            raise ValueError(f"Hay valores duplicados de '{identity}' en '{key}'.")

    required_fields = {
        "monedas": ("nombre", "simbolo", "decimales", "formato", "regla_redondeo"),
        "idiomas": ("nombre", "habilitado"),
        "paises": (
            "nombre",
            "moneda_local",
            "idioma_predeterminado",
            "zona_horaria",
            "habilitado",
        ),
        "impuestos": (
            "pais_codigo",
            "nombre",
            "tasa",
            "incluido_en_precio",
            "vigente_desde",
        ),
        "bodegas": ("nombre", "pais_codigo", "activo"),
        "roles": ("nombre",),
        "permisos": ("descripcion",),
        "parametros": ("valor", "tipo"),
    }
    text_fields = {
        "monedas": ("codigo_iso", "nombre", "simbolo", "formato", "regla_redondeo"),
        "idiomas": ("codigo_bcp47", "nombre"),
        "paises": (
            "codigo_iso",
            "nombre",
            "moneda_local",
            "idioma_predeterminado",
            "zona_horaria",
        ),
        "impuestos": ("pais_codigo", "nombre", "vigente_desde"),
        "bodegas": ("codigo", "nombre", "pais_codigo"),
        "roles": ("codigo", "nombre"),
        "permisos": ("codigo", "descripcion"),
        "parametros": ("clave", "tipo"),
    }
    for collection, fields in required_fields.items():
        for record in data[collection]:
            if any(field not in record for field in fields):
                raise ValueError(f"Faltan campos requeridos en un elemento de '{collection}'.")
            if not all(
                isinstance(record[field], str) and record[field].strip()
                for field in text_fields[collection]
            ):
                raise ValueError(
                    f"Los campos de texto de '{collection}' deben ser cadenas no vacías."
                )

    currency_codes = {record["codigo_iso"] for record in data["monedas"]}
    language_codes = {record["codigo_bcp47"] for record in data["idiomas"]}
    country_codes = {record["codigo_iso"] for record in data["paises"]}
    role_codes = {record["codigo"] for record in data["roles"]}
    permission_codes = {record["codigo"] for record in data["permisos"]}
    if "ADMINISTRADOR" not in role_codes:
        raise ValueError("Falta el rol inicial 'ADMINISTRADOR'.")
    if "es" not in language_codes:
        raise ValueError("Falta el idioma inicial 'es' para el usuario administrador.")
    if "ADMINISTRADOR" not in data["permisos_por_rol"]:
        raise ValueError("Falta la asignación de permisos del rol 'ADMINISTRADOR'.")
    for country in data["paises"]:
        if country.get("moneda_local") not in currency_codes:
            raise ValueError(f"País '{country['codigo_iso']}' referencia una moneda inexistente.")
        if country.get("idioma_predeterminado") not in language_codes:
            raise ValueError(f"País '{country['codigo_iso']}' referencia un idioma inexistente.")
        if not isinstance(country["habilitado"], bool):
            raise ValueError(f"El estado de '{country['codigo_iso']}' debe ser booleano.")
    for currency in data["monedas"]:
        if not isinstance(currency["decimales"], int) or isinstance(currency["decimales"], bool):
            raise ValueError(f"Los decimales de '{currency['codigo_iso']}' deben ser enteros.")
        if not 0 <= currency["decimales"] <= 4:
            raise ValueError(
                f"Los decimales de '{currency['codigo_iso']}' deben estar entre 0 y 4."
            )
    for language in data["idiomas"]:
        if not isinstance(language["habilitado"], bool):
            raise ValueError(f"El estado de '{language['codigo_bcp47']}' debe ser booleano.")
    for warehouse in data["bodegas"]:
        if not isinstance(warehouse["activo"], bool):
            raise ValueError(f"El estado de la bodega '{warehouse['codigo']}' debe ser booleano.")
        if warehouse["pais_codigo"] not in country_codes:
            raise ValueError(f"La bodega '{warehouse['codigo']}' referencia un país inexistente.")
    for tax in data["impuestos"]:
        if tax.get("pais_codigo") not in country_codes:
            raise ValueError("Una tarifa inicial referencia un país inexistente.")
        if (
            not isinstance(tax["tasa"], int | float)
            or isinstance(tax["tasa"], bool)
            or not math.isfinite(tax["tasa"])
        ):
            raise ValueError("Cada tasa inicial debe ser numérica.")
        if tax["tasa"] < 0:
            raise ValueError("La tasa de impuesto no puede ser negativa.")
        if not isinstance(tax["incluido_en_precio"], bool):
            raise ValueError("Cada impuesto debe indicar si está incluido en el precio.")
        try:
            date.fromisoformat(tax["vigente_desde"])
        except ValueError as exc:
            raise ValueError("Las fechas de vigencia deben usar el formato AAAA-MM-DD.") from exc
    if set(data["permisos_por_rol"]) - role_codes:
        raise ValueError("El mapa de permisos referencia roles no definidos.")
    for assigned_permissions in data["permisos_por_rol"].values():
        if not isinstance(assigned_permissions, list):
            raise ValueError("Cada asignación de permisos debe ser una lista.")
        if not all(isinstance(code, str) for code in assigned_permissions):
            raise ValueError("Los códigos de permiso deben ser texto.")
        if set(assigned_permissions) - permission_codes:
            raise ValueError("El mapa de roles referencia permisos no definidos.")
    parameter_identities = set()
    for parameter in data["parametros"]:
        if not isinstance(parameter, dict) or not str(parameter.get("clave", "")).strip():
            raise ValueError("Cada elemento de 'parametros' debe incluir 'clave'.")
        country = parameter.get("pais_codigo")
        if country is not None and country not in country_codes:
            raise ValueError(f"El parámetro '{parameter['clave']}' referencia un país inexistente.")
        identity = (parameter["clave"], country)
        if identity in parameter_identities:
            raise ValueError(f"El parámetro '{parameter['clave']}' está duplicado para su ámbito.")
        parameter_identities.add(identity)
    for parameter in data["parametros"]:
        parameter_type = parameter["tipo"]
        if parameter_type not in {"TEXTO", "ENTERO", "DECIMAL", "BOOLEANO", "JSON"}:
            raise ValueError(f"Tipo no admitido para el parámetro '{parameter['clave']}'.")
        value = parameter.get("valor")
        if parameter_type == "TEXTO" and not isinstance(value, str):
            raise ValueError(f"El parámetro '{parameter['clave']}' debe ser texto.")
        if parameter_type == "ENTERO" and (not isinstance(value, int) or isinstance(value, bool)):
            raise ValueError(f"El parámetro '{parameter['clave']}' debe ser entero.")
        if parameter_type == "DECIMAL" and (
            not isinstance(value, int | float)
            or isinstance(value, bool)
            or not math.isfinite(value)
        ):
            raise ValueError(f"El parámetro '{parameter['clave']}' debe ser numérico.")
        if parameter_type == "BOOLEANO" and not isinstance(value, bool):
            raise ValueError(f"El parámetro '{parameter['clave']}' debe ser booleano.")
        for bound in ("minimo", "maximo"):
            if bound in parameter and (
                not isinstance(parameter[bound], int | float)
                or isinstance(parameter[bound], bool)
                or not math.isfinite(parameter[bound])
            ):
                raise ValueError(
                    f"El límite '{bound}' de '{parameter['clave']}' debe ser numérico."
                )
        if any(bound in parameter for bound in ("minimo", "maximo")) and parameter_type not in {
            "ENTERO",
            "DECIMAL",
        }:
            raise ValueError(
                f"Los límites de '{parameter['clave']}' solo aplican a valores numéricos."
            )
        if "minimo" in parameter and value < parameter["minimo"]:
            raise ValueError(f"El valor de '{parameter['clave']}' es menor que su mínimo.")
        if "maximo" in parameter and value > parameter["maximo"]:
            raise ValueError(f"El valor de '{parameter['clave']}' es mayor que su máximo.")
        if "minimo" in parameter and "maximo" in parameter:
            if parameter["minimo"] > parameter["maximo"]:
                raise ValueError(f"Rango inválido en '{parameter['clave']}'.")


def _referencias(data: dict[str, Any]) -> tuple[dict[str, Any], dict[str, Any]]:
    currency_ids = {
        currency["codigo_iso"]: _id("moneda", currency["codigo_iso"])
        for currency in data["monedas"]
    }
    language_ids = {
        language["codigo_bcp47"]: _id("idioma", language["codigo_bcp47"])
        for language in data["idiomas"]
    }
    country_ids = {
        country["codigo_iso"]: _id("pais", country["codigo_iso"]) for country in data["paises"]
    }
    return (
        {"moneda": currency_ids, "idioma": language_ids, "pais": country_ids},
        {role["codigo"]: _id("rol", role["codigo"]) for role in data["roles"]},
    )


def sembrar_datos(
    connection: psycopg.Connection,
    seed_path: Path,
    admin_email: str,
    admin_password: str,
) -> dict[str, int]:
    """Inserta catálogos iniciales y administrador sin modificar filas existentes."""
    email = admin_email.strip().lower()
    if not email or "@" not in email:
        raise ValueError("ADMIN_EMAIL debe contener una dirección de correo válida.")
    password_bytes = admin_password.encode("utf-8")
    if not password_bytes:
        raise ValueError("ADMIN_PASSWORD no puede estar vacío.")
    if len(password_bytes) > 72:
        raise ValueError("ADMIN_PASSWORD supera el límite de 72 bytes de BCrypt.")

    data = _load_seed_data(seed_path)
    ref_ids, role_ids = _referencias(data)
    counts: dict[str, int] = {}

    def add(table: str, record: dict[str, Any], json_fields: set[str] | None = None) -> None:
        counts[table] = counts.get(table, 0) + int(_insert(connection, table, record, json_fields))

    for currency in data["monedas"]:
        add("moneda", {"id": ref_ids["moneda"][currency["codigo_iso"]], **currency})
    for language in data["idiomas"]:
        add("idioma", {"id": ref_ids["idioma"][language["codigo_bcp47"]], **language})
    for country in data["paises"]:
        add(
            "pais",
            {
                "id": ref_ids["pais"][country["codigo_iso"]],
                "codigo_iso": country["codigo_iso"],
                "nombre": country["nombre"],
                "moneda_local_id": ref_ids["moneda"][country["moneda_local"]],
                "idioma_predeterminado_id": ref_ids["idioma"][country["idioma_predeterminado"]],
                "zona_horaria": country["zona_horaria"],
                "habilitado": country["habilitado"],
            },
        )

    for role in data["roles"]:
        add(
            "rol",
            {
                "id": role_ids[role["codigo"]],
                "codigo": role["codigo"],
                "nombre": role["nombre"],
                "activo": True,
            },
        )
    permission_ids = {}
    for permission in data["permisos"]:
        permission_ids[permission["codigo"]] = _id("permiso", permission["codigo"])
        add(
            "permiso",
            {
                "id": permission_ids[permission["codigo"]],
                "codigo": permission["codigo"],
                "descripcion": permission["descripcion"],
            },
        )
    for role_code, permission_codes in data["permisos_por_rol"].items():
        for permission_code in permission_codes:
            add(
                "rol_permiso",
                {
                    "id": _id("rol_permiso", f"{role_code}:{permission_code}"),
                    "rol_id": role_ids[role_code],
                    "permiso_id": permission_ids[permission_code],
                },
            )

    administrator_id = _id("usuario", email)
    password_hash = bcrypt.hashpw(password_bytes, bcrypt.gensalt(rounds=12)).decode("ascii")
    add(
        "usuario",
        {
            "id": administrator_id,
            "correo": email,
            "nombre": "Administrador",
            "hash_contrasena": password_hash,
            "idioma_id": ref_ids["idioma"]["es"],
            "intentos_fallidos": 0,
            "activo": True,
            "creado_en_utc": datetime.now(UTC),
        },
    )
    add(
        "usuario_rol",
        {
            "id": _id("usuario_rol", f"{email}:ADMINISTRADOR"),
            "usuario_id": administrator_id,
            "rol_id": role_ids["ADMINISTRADOR"],
        },
    )

    for warehouse in data["bodegas"]:
        add(
            "bodega",
            {
                "id": _id("bodega", warehouse["codigo"]),
                "codigo": warehouse["codigo"],
                "nombre": warehouse["nombre"],
                "activo": warehouse["activo"],
                "categoria_id": None,
                "pais_id": ref_ids["pais"][warehouse["pais_codigo"]],
            },
        )

    for tax in data["impuestos"]:
        add(
            "impuesto",
            {
                "id": _id("impuesto", f"{tax['pais_codigo']}:{tax['nombre']}"),
                "pais_id": ref_ids["pais"][tax["pais_codigo"]],
                "categoria_id": None,
                "nombre": tax["nombre"],
                "tasa": Decimal(str(tax["tasa"])),
                "incluido_en_precio": tax["incluido_en_precio"],
                "vigente_desde": tax["vigente_desde"],
            },
        )

    for parameter in data["parametros"]:
        value = parameter["valor"]
        country = parameter.get("pais_codigo")
        add(
            "parametro",
            {
                "id": _id(
                    "parametro",
                    parameter["clave"] if country is None else f"{parameter['clave']}:{country}",
                ),
                "clave": parameter["clave"],
                "valor": value,
                "tipo": parameter["tipo"],
                "minimo": (Decimal(str(parameter["minimo"])) if "minimo" in parameter else None),
                "maximo": (Decimal(str(parameter["maximo"])) if "maximo" in parameter else None),
                "valor_por_defecto": value,
                "ambito": "NEGOCIO" if country is None else "PAIS",
                "pais_id": None if country is None else ref_ids["pais"][country],
                "vigente_desde": "2026-01-01",
                "vigente_hasta": None,
                "actualizado_por_id": administrator_id,
                "actualizado_en_utc": datetime.now(UTC),
            },
            {"valor", "valor_por_defecto"},
        )

    return counts
