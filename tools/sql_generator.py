"""Generación de SQL PostgreSQL, metadatos resueltos y CRUD JDBC."""

from __future__ import annotations

import heapq
import json
import re
from decimal import Decimal, InvalidOperation
from hashlib import sha256
from pathlib import Path
from typing import Any

from jinja2 import Environment, FileSystemLoader, StrictUndefined

if __package__:
    from .model_validator import validar_modelo
else:
    from model_validator import validar_modelo

GENERATED_HEADER = "GENERADO — no editar; modifique el JSON del modelo o la plantilla."
TYPES = {
    "uuid": "UUID",
    "texto": "TEXT",
    "entero": "INTEGER",
    "booleano": "BOOLEAN",
    "fecha": "DATE",
    "fecha_hora_utc": "TIMESTAMPTZ",
    "json": "JSONB",
}


class GeneratedFileError(ValueError):
    """Un artefacto existente no se reconoce como generado por esta herramienta."""


def _ident(value: str) -> str:
    if not re.fullmatch(r"[a-z][a-z0-9_]*", value):
        raise ValueError(f"Identificador SQL inválido: {value!r}.")
    return f'"{value}"'


def _constraint_name(parts: list[str]) -> str:
    raw_name = "_".join(parts)
    if len(raw_name) <= 63:
        return raw_name
    suffix = sha256(raw_name.encode("utf-8")).hexdigest()[:10]
    return f"{raw_name[:52]}_{suffix}"


def _quote_text(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def _sql_number(value: int | float, field_name: str) -> str:
    if isinstance(value, bool):
        raise ValueError(f"El valor numérico de {field_name} no puede ser booleano.")
    try:
        number = Decimal(str(value))
    except InvalidOperation as exc:
        raise ValueError(f"Valor numérico inválido en {field_name}: {value!r}.") from exc
    if not number.is_finite():
        raise ValueError(f"El valor numérico de {field_name} debe ser finito.")
    return format(number, "f")


def _default_sql(field: dict[str, Any]) -> str | None:
    if "default" not in field:
        return None

    value = field["default"]
    field_name = f"{field.get('nombre', 'campo')}.default"
    field_type = field["tipo"]
    if field_type == "json":
        encoded = json.dumps(value, ensure_ascii=False, separators=(",", ":"))
        return f"{_quote_text(encoded)}::JSONB"
    if field_type == "texto":
        if not isinstance(value, str):
            raise ValueError(f"El valor por defecto de {field_name} debe ser texto.")
        return _quote_text(value)
    if field_type == "booleano":
        if not isinstance(value, bool):
            raise ValueError(f"El valor por defecto de {field_name} debe ser booleano.")
        return "TRUE" if value else "FALSE"
    if field_type == "entero":
        if not isinstance(value, int) or isinstance(value, bool):
            raise ValueError(f"El valor por defecto de {field_name} debe ser entero.")
        return str(value)
    if field_type == "decimal":
        return _sql_number(value, field_name)
    raise ValueError(f"No se admite un valor por defecto para {field_type} en {field_name}.")


def _field_type(field: dict[str, Any]) -> str:
    field_type = field["tipo"]
    if field_type == "decimal":
        return f"NUMERIC({field['precision']},{field['escala']})"
    return TYPES[field_type]


def _column_definition(entity: dict[str, Any], field: dict[str, Any]) -> str:
    name = field["nombre"]
    parts = [f"{_ident(name)} {_field_type(field)}"]
    if field.get("pk", False):
        parts.append("PRIMARY KEY")
    elif field.get("obligatorio", False):
        parts.append("NOT NULL")

    default_value = _default_sql(field)
    if default_value is not None:
        parts.append(f"DEFAULT {default_value}")
    return " ".join(parts)


def _entity_statement(entity: dict[str, Any]) -> str:
    entity_name = entity["nombre"]
    definitions = [_column_definition(entity, field) for field in entity["campos"]]

    for field in entity["campos"]:
        reference = field.get("ref")
        if reference is None:
            continue
        constraint = _constraint_name(["fk", entity_name, field["nombre"]])
        definitions.append(
            f"CONSTRAINT {_ident(constraint)} FOREIGN KEY ({_ident(field['nombre'])}) "
            f"REFERENCES public.{_ident(reference['entidad'])}"
            f" ({_ident(reference['campo'])}) ON DELETE RESTRICT"
        )

    for field in entity["campos"]:
        if "min" in field:
            minimum = _sql_number(field["min"], f"{entity_name}.{field['nombre']}.min")
            check_name = _constraint_name(["ck", entity_name, field["nombre"], "min"])
            definitions.append(
                f"CONSTRAINT {_ident(check_name)} CHECK ({_ident(field['nombre'])} >= {minimum})"
            )
        if "max" in field:
            maximum = _sql_number(field["max"], f"{entity_name}.{field['nombre']}.max")
            check_name = _constraint_name(["ck", entity_name, field["nombre"], "max"])
            definitions.append(
                f"CONSTRAINT {_ident(check_name)} CHECK ({_ident(field['nombre'])} <= {maximum})"
            )
        if "valores" in field:
            choices = ", ".join(_quote_text(value) for value in field["valores"])
            check_name = _constraint_name(["ck", entity_name, field["nombre"], "valores"])
            definitions.append(
                f"CONSTRAINT {_ident(check_name)} CHECK ({_ident(field['nombre'])} IN ({choices}))"
            )

    for unique_fields in entity.get("unicos", []):
        name = _constraint_name(["uq", entity_name, *unique_fields])
        columns = ", ".join(_ident(field) for field in unique_fields)
        definitions.append(f"CONSTRAINT {_ident(name)} UNIQUE ({columns})")

    joined_definitions = ",\n    ".join(definitions)
    return (
        f"CREATE TABLE IF NOT EXISTS public.{_ident(entity_name)} (\n    {joined_definitions}\n);"
    )


def generar_sql(modules: list[dict[str, Any]], template_dir: Path) -> str:
    statements: list[str] = []
    all_entities = [entity for module in modules for entity in module["entidades"]]
    entities = _ordenar_entidades(all_entities)

    for entity in entities:
        statements.append(_entity_statement(entity))

    for entity in entities:
        for index_number, index_fields in enumerate(entity.get("indices", []), start=1):
            index_name = _constraint_name(
                ["ix", entity["nombre"], str(index_number), *index_fields]
            )
            columns = ", ".join(_ident(field) for field in index_fields)
            statements.append(
                f"CREATE INDEX IF NOT EXISTS {_ident(index_name)} "
                f"ON public.{_ident(entity['nombre'])} ({columns});"
            )

    immutable_entities = [entity for entity in entities if entity["inmutable"]]
    if immutable_entities:
        statements.append(
            "CREATE OR REPLACE FUNCTION public.rechazar_cambio_inmutable() "
            "RETURNS trigger LANGUAGE plpgsql AS $$ "
            "BEGIN RAISE EXCEPTION 'La tabla % es inmutable', TG_TABLE_NAME "
            "USING ERRCODE = '55000'; END; $$;"
        )
        for entity in immutable_entities:
            trigger_name = _constraint_name(["trg", entity["nombre"], "inmutable"])
            statements.append(
                f"DROP TRIGGER IF EXISTS {_ident(trigger_name)} "
                f"ON public.{_ident(entity['nombre'])};"
            )
            statements.append(
                f"CREATE TRIGGER {_ident(trigger_name)} BEFORE UPDATE OR DELETE "
                f"ON public.{_ident(entity['nombre'])} FOR EACH ROW "
                "EXECUTE FUNCTION public.rechazar_cambio_inmutable();"
            )

    deactivation_entities = [entity for entity in entities if entity.get("solo_desactivar")]
    if deactivation_entities:
        statements.append(
            "CREATE OR REPLACE FUNCTION public.rechazar_eliminacion_producto() "
            "RETURNS trigger LANGUAGE plpgsql AS $$ "
            "BEGIN RAISE EXCEPTION 'Los productos solo se pueden desactivar' "
            "USING ERRCODE = '55000'; END; $$;"
        )
        for entity in deactivation_entities:
            trigger_name = _constraint_name(["trg", entity["nombre"], "sin_delete"])
            statements.append(
                f"DROP TRIGGER IF EXISTS {_ident(trigger_name)} "
                f"ON public.{_ident(entity['nombre'])};"
            )
            statements.append(
                f"CREATE TRIGGER {_ident(trigger_name)} BEFORE DELETE "
                f"ON public.{_ident(entity['nombre'])} FOR EACH ROW "
                "EXECUTE FUNCTION public.rechazar_eliminacion_producto();"
            )

    environment = Environment(
        loader=FileSystemLoader(template_dir),
        undefined=StrictUndefined,
        autoescape=False,
        keep_trailing_newline=True,
    )
    template = environment.get_template("migration.sql.j2")
    return template.render(statements=statements).rstrip() + "\n"


def _ordenar_entidades(entities: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Ordena tablas por dependencias FK; conserva un orden determinista."""
    by_name = {entity["nombre"]: entity for entity in entities}
    positions = {entity["nombre"]: index for index, entity in enumerate(entities)}
    dependencies = {
        entity["nombre"]: {
            field["ref"]["entidad"]
            for field in entity["campos"]
            if "ref" in field and field["ref"]["entidad"] != entity["nombre"]
        }
        for entity in entities
    }
    dependents: dict[str, set[str]] = {name: set() for name in by_name}
    for entity_name, targets in dependencies.items():
        for target in targets:
            dependents[target].add(entity_name)

    ready = [(positions[name], name) for name, targets in dependencies.items() if not targets]
    heapq.heapify(ready)
    ordered: list[dict[str, Any]] = []
    while ready:
        _, current = heapq.heappop(ready)
        ordered.append(by_name[current])
        for dependent in dependents[current]:
            dependencies[dependent].remove(current)
            if not dependencies[dependent]:
                heapq.heappush(ready, (positions[dependent], dependent))

    if len(ordered) != len(entities):
        unresolved = sorted(name for name, targets in dependencies.items() if targets)
        raise ValueError(
            "No se pueden ordenar las entidades por sus referencias; "
            f"hay dependencias circulares entre: {', '.join(unresolved)}."
        )
    return ordered


def cargar_modulos(model_dir: Path) -> list[dict[str, Any]]:
    modules = []
    for module_path in sorted(model_dir.glob("*.json")):
        with module_path.open(encoding="utf-8") as module_file:
            modules.append(json.load(module_file))
    return modules


def crear_schema_resuelto(modules: list[dict[str, Any]]) -> dict[str, Any]:
    entities = []
    for module in modules:
        for entity in module["entidades"]:
            entities.append({"modulo": module["modulo"], **entity})
    return {
        "_cabecera": GENERATED_HEADER,
        "version_modelo": 1,
        "entidades": entities,
    }


def generar_crud_java(modules: list[dict[str, Any]]) -> str:
    """Genera las sentencias CRUD JDBC para los catálogos de parametrización."""
    required_entities = ("pais", "moneda", "idioma", "tipo_cambio", "impuesto")
    return _generar_crud_java(
        modules,
        "parametrizacion",
        required_entities,
        "ParametrizacionCrudSql",
    )


def generar_catalogo_crud_java(modules: list[dict[str, Any]]) -> str:
    """Genera las sentencias CRUD JDBC para las entidades del catálogo."""
    required_entities = (
        "categoria",
        "tipo_producto",
        "atributo_definicion",
        "producto",
    )
    return _generar_crud_java(
        modules,
        "catalogo",
        required_entities,
        "CatalogoCrudSql",
    )


def generar_inventario_crud_java(modules: list[dict[str, Any]]) -> str:
    """Genera las sentencias JDBC de las bodegas."""
    return _generar_crud_java(
        modules,
        "inventario",
        ("bodega",),
        "InventarioCrudSql",
    )


def _generar_crud_java(
    modules: list[dict[str, Any]],
    module_name: str,
    required_entities: tuple[str, ...],
    class_name: str,
) -> str:
    entities = {
        entity["nombre"]: entity
        for module in modules
        for entity in module["entidades"]
        if module["modulo"] == module_name
    }
    missing_entities = [
        name
        for name in required_entities
        if name not in entities or not entities[name]["generar_crud"]
    ]
    if missing_entities:
        raise ValueError(
            f"El CRUD del módulo '{module_name}' requiere entidades "
            "con generar_crud=true: " + ", ".join(missing_entities) + "."
        )

    lines = [
        f"// {GENERATED_HEADER}",
        "package com.inventario.generated;",
        "",
        f"public final class {class_name} {{",
        f"    private {class_name}() {{",
        "    }",
        "",
    ]
    for name in required_entities:
        entity = entities[name]
        fields = entity["campos"]
        primary_keys = [field["nombre"] for field in fields if field.get("pk")]
        if primary_keys != ["id"]:
            raise ValueError(
                f"El CRUD generado de '{name}' requiere una clave primaria simple 'id'."
            )

        columns = [_ident(field["nombre"]) for field in fields]
        table = f"public.{_ident(name)}"
        prefix = name.upper()
        sql_statements = {
            "COLUMNS": ", ".join(columns),
            "SELECT_ALL": f"SELECT {', '.join(columns)} FROM {table}",
            "SELECT_BY_ID": (f"SELECT {', '.join(columns)} FROM {table} WHERE {_ident('id')} = ?"),
            "INSERT": (
                f"INSERT INTO {table} ({', '.join(columns)}) "
                f"VALUES ({', '.join('?' for _ in columns)})"
            ),
            "UPDATE": (
                f"UPDATE {table} SET "
                + ", ".join(
                    f"{_ident(field['nombre'])} = ?" for field in fields if field["nombre"] != "id"
                )
                + f" WHERE {_ident('id')} = ?"
            ),
            "DELETE": f"DELETE FROM {table} WHERE {_ident('id')} = ?",
        }
        if entity.get("solo_desactivar"):
            del sql_statements["DELETE"]
        for operation, sql in sql_statements.items():
            constant = f"{prefix}_{operation}"
            lines.extend(_java_string_constant(constant, sql))
        lines.append("")
    lines.extend(["}", ""])
    return "\n".join(lines)


def _java_string_constant(name: str, value: str) -> list[str]:
    parts: list[str] = []
    current = ""
    for word in value.split(" "):
        candidate = word if not current else f"{current} {word}"
        if len(candidate) > 68 and current:
            parts.append(current)
            current = word
        else:
            current = candidate
    if current:
        parts.append(current)

    lines = [f"    public static final String {name} ="]
    for index, part in enumerate(parts):
        suffix = " " if index < len(parts) - 1 else ""
        line = "            " + json.dumps(part + suffix, ensure_ascii=False)
        lines.append(line + (";" if index == len(parts) - 1 else " +"))
    return lines


def verificar_cabecera(path: Path, file_type: str) -> None:
    if not path.exists():
        return
    if file_type in {"sql", "java"}:
        first_line = path.read_text(encoding="utf-8").splitlines()[:1]
        if not first_line or GENERATED_HEADER not in first_line[0]:
            raise GeneratedFileError(
                f"Se rehúsa sobrescribir '{path}': falta la cabecera de archivo generado."
            )
        return

    try:
        content = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise GeneratedFileError(
            f"Se rehúsa sobrescribir '{path}': no se pudo verificar su cabecera."
        ) from exc
    if not isinstance(content, dict) or content.get("_cabecera") != GENERATED_HEADER:
        raise GeneratedFileError(
            f"Se rehúsa sobrescribir '{path}': falta la cabecera de archivo generado."
        )


def generar_archivos(root: Path, dry_run: bool = False) -> list[Path]:
    model_dir = root / "db" / "model" / "modulos"
    schema_path = root / "db" / "model" / "model.schema.json"
    template_dir = root / "tools" / "templates"
    errors, _ = validar_modelo(model_dir, schema_path)
    if errors:
        raise ValueError("No se genera SQL porque el modelo no es válido:\n" + "\n".join(errors))

    modules = cargar_modulos(model_dir)
    sql_content = generar_sql(modules, template_dir)
    resolved_content = (
        json.dumps(
            crear_schema_resuelto(modules),
            ensure_ascii=False,
            indent=2,
        )
        + "\n"
    )
    crud_java_content = generar_crud_java(modules)
    catalog_crud_java_content = generar_catalogo_crud_java(modules)
    inventory_crud_java_content = generar_inventario_crud_java(modules)
    outputs = [
        (root / "db" / "migrations" / "V001__modelo_inicial.sql", sql_content, "sql"),
        (root / "db" / "model" / "schema-resuelto.json", resolved_content, "json"),
        (
            root
            / "backend"
            / "src"
            / "main"
            / "java"
            / "com"
            / "inventario"
            / "generated"
            / "ParametrizacionCrudSql.java",
            crud_java_content,
            "java",
        ),
        (
            root
            / "backend"
            / "src"
            / "main"
            / "java"
            / "com"
            / "inventario"
            / "generated"
            / "CatalogoCrudSql.java",
            catalog_crud_java_content,
            "java",
        ),
        (
            root
            / "backend"
            / "src"
            / "main"
            / "java"
            / "com"
            / "inventario"
            / "generated"
            / "InventarioCrudSql.java",
            inventory_crud_java_content,
            "java",
        ),
    ]

    for path, _, file_type in outputs:
        verificar_cabecera(path, file_type)
        if not dry_run:
            path.parent.mkdir(parents=True, exist_ok=True)
    if not dry_run:
        for path, content, _ in outputs:
            path.write_text(content, encoding="utf-8")
    return [path for path, _, _ in outputs]
