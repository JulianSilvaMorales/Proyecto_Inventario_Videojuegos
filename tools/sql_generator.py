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
FRONTEND_CRUD = {
    "pais": {
        "ruta_api": "/api/v1/configuracion/paises",
        "modulo_frontend": "settings",
        "permisos_consulta": ["PARAMETROS_GESTIONAR"],
        "permisos_gestion": ["PARAMETROS_GESTIONAR"],
        "eliminacion": "delete",
    },
    "moneda": {
        "ruta_api": "/api/v1/configuracion/monedas",
        "modulo_frontend": "settings",
        "permisos_consulta": ["PARAMETROS_GESTIONAR"],
        "permisos_gestion": ["PARAMETROS_GESTIONAR"],
        "eliminacion": "delete",
    },
    "idioma": {
        "ruta_api": "/api/v1/configuracion/idiomas",
        "modulo_frontend": "settings",
        "permisos_consulta": ["PARAMETROS_GESTIONAR"],
        "permisos_gestion": ["PARAMETROS_GESTIONAR"],
        "eliminacion": "delete",
    },
    "tipo_cambio": {
        "ruta_api": "/api/v1/configuracion/tipos-cambio",
        "modulo_frontend": "settings",
        "permisos_consulta": ["TIPOS_CAMBIO_GESTIONAR"],
        "permisos_gestion": ["TIPOS_CAMBIO_GESTIONAR"],
        "eliminacion": "delete",
    },
    "impuesto": {
        "ruta_api": "/api/v1/configuracion/impuestos",
        "modulo_frontend": "settings",
        "permisos_consulta": ["PARAMETROS_GESTIONAR"],
        "permisos_gestion": ["PARAMETROS_GESTIONAR"],
        "eliminacion": "delete",
    },
    "categoria": {
        "ruta_api": "/api/v1/catalogo/categorias",
        "modulo_frontend": "catalog",
        "permisos_consulta": ["CATALOGO_CONSULTAR"],
        "permisos_gestion": ["PRODUCTOS_GESTIONAR"],
        "eliminacion": "delete",
    },
    "bodega": {
        "ruta_api": "/api/v1/inventario/bodegas",
        "modulo_frontend": "inventory",
        "permisos_consulta": ["INVENTARIO_CONSULTAR"],
        "permisos_gestion": ["COMPRAS_GESTIONAR"],
        "eliminacion": "deactivate",
    },
    "proveedor": {
        "ruta_api": "/api/v1/proveedores",
        "modulo_frontend": "suppliers",
        "permisos_consulta": ["PROVEEDORES_CONSULTAR", "PROVEEDORES_GESTIONAR"],
        "permisos_gestion": ["PROVEEDORES_GESTIONAR"],
        "eliminacion": "deactivate",
    },
    "cliente": {
        "ruta_api": "/api/v1/clientes",
        "modulo_frontend": "sales",
        "permisos_consulta": ["CLIENTES_GESTIONAR"],
        "permisos_gestion": ["CLIENTES_GESTIONAR"],
        "eliminacion": "deactivate",
    },
}
FIELD_LABELS = {
    "activo": ("Activo", "Active"),
    "apellidos": ("Apellidos", "Last name"),
    "actualizado_por_id": ("Actualizado por", "Updated by"),
    "base_legal": ("Base legal", "Legal basis"),
    "categoria_id": ("Categoría", "Category"),
    "clasificacion": ("Clasificación", "Classification"),
    "codigo": ("Código", "Code"),
    "codigo_bcp47": ("Código BCP 47", "BCP 47 code"),
    "codigo_iso": ("Código ISO", "ISO code"),
    "codigo_postal": ("Código postal", "Postal code"),
    "condiciones_pago": ("Condiciones de pago", "Payment terms"),
    "consentimiento_en_utc": ("Consentimiento registrado (UTC)", "Consent recorded (UTC)"),
    "consentimiento_registrado": ("Consentimiento registrado", "Consent recorded"),
    "correo": ("Correo electrónico", "Email"),
    "decimales": ("Decimales", "Decimal places"),
    "direccion": ("Dirección", "Address"),
    "documento": ("Documento", "Document"),
    "formato": ("Formato", "Format"),
    "habilitado": ("Habilitado", "Enabled"),
    "idioma_predeterminado_id": ("Idioma predeterminado", "Default language"),
    "id": ("Identificador", "Identifier"),
    "identificacion_tributaria": ("Identificación tributaria", "Tax ID"),
    "incluido_en_precio": ("Incluido en el precio", "Included in price"),
    "moneda_compra_id": ("Moneda de compra", "Purchase currency"),
    "moneda_destino_id": ("Moneda destino", "Target currency"),
    "moneda_local_id": ("Moneda local", "Local currency"),
    "moneda_origen_id": ("Moneda origen", "Source currency"),
    "nombre": ("Nombre", "Name"),
    "origen": ("Origen", "Source"),
    "pais_id": ("País", "Country"),
    "padre_id": ("Categoría superior", "Parent category"),
    "persona_contacto": ("Persona de contacto", "Contact person"),
    "razon_social": ("Razón social", "Business name"),
    "regla_redondeo": ("Regla de redondeo", "Rounding rule"),
    "simbolo": ("Símbolo", "Symbol"),
    "tasa": ("Tasa", "Rate"),
    "telefono": ("Teléfono", "Phone"),
    "tipo_documento": ("Tipo de documento", "Document type"),
    "vigente_desde": ("Vigente desde", "Valid from"),
    "vigente_desde_utc": ("Vigente desde (UTC)", "Valid from (UTC)"),
    "vigente_hasta": ("Vigente hasta", "Valid until"),
    "zona_horaria": ("Zona horaria", "Time zone"),
    "ultimo_contacto_en_utc": ("Último contacto (UTC)", "Last contact (UTC)"),
}
FRONTEND_TS_TYPES = {
    "booleano": "boolean",
    "decimal": "number",
    "entero": "number",
    "fecha": "string",
    "fecha_hora_utc": "string",
    "json": "unknown",
    "texto": "string",
    "uuid": "string",
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


def generar_entidades_frontend(modules: list[dict[str, Any]]) -> dict[str, Any]:
    """Construye el contrato del CRUD web para las entidades simples."""
    entities = {entity["nombre"]: entity for module in modules for entity in module["entidades"]}
    metadata = []
    for name, config in FRONTEND_CRUD.items():
        entity = entities.get(name)
        if entity is None or not entity.get("generar_crud"):
            raise ValueError(
                f"El CRUD frontend requiere la entidad '{name}' con generar_crud=true."
            )
        fields = []
        for field in entity["campos"]:
            field_name = field["nombre"]
            labels = FIELD_LABELS.get(field_name)
            if labels is None:
                words = field_name.replace("_", " ").split()
                fallback_label = " ".join(word.capitalize() for word in words)
                labels = (fallback_label, fallback_label)
            read_only = field.get("pk", False) or field.get("solo_lectura", False)
            fields.append(
                {
                    "nombre": field_name,
                    "apiName": _camel_case(field_name),
                    "tipo": field["tipo"],
                    "typescript": FRONTEND_TS_TYPES[field["tipo"]],
                    "obligatorio": field.get("obligatorio", False) or field.get("pk", False),
                    "soloLectura": read_only,
                    "min": field.get("min"),
                    "max": field.get("max"),
                    "default": field.get("default"),
                    "valores": field.get("valores", []),
                    "referencia": field.get("ref", {}).get("entidad"),
                    "etiquetas": {"es": labels[0], "en": labels[1]},
                }
            )
        metadata.append(
            {
                "nombre": name,
                "modulo": config["modulo_frontend"],
                "rutaApi": config["ruta_api"],
                "permisosConsulta": config["permisos_consulta"],
                "permisosGestion": config["permisos_gestion"],
                "eliminacion": (
                    "deactivate" if entity.get("solo_desactivar") else config["eliminacion"]
                ),
                "validacionesCondicionales": [
                    {
                        "campo": _camel_case(rule["campo"]),
                        "valor": rule.get("valor"),
                        "camposRequeridos": [
                            _camel_case(field) for field in rule.get("requeridos", [])
                        ],
                        "requiereVerdadero": rule.get("requiere_verdadero", False),
                        "soloCrear": rule.get("solo_creacion", False),
                    }
                    for rule in entity.get("validaciones_frontend", [])
                ],
                "etiquetas": entity["etiquetas"],
                "campos": fields,
            }
        )
    return {"_cabecera": GENERATED_HEADER, "entidades": metadata}


def _camel_case(name: str) -> str:
    parts = name.split("_")
    return parts[0] + "".join(part.capitalize() for part in parts[1:])


def generar_tipos_frontend(metadata: dict[str, Any]) -> str:
    """Genera interfaces TypeScript para las entidades del CRUD."""
    lines = [f"// {GENERATED_HEADER}", ""]
    for entity in metadata["entidades"]:
        interface_name = "".join(part.capitalize() for part in entity["nombre"].split("_"))
        lines.append(f"export interface {interface_name} {{")
        for field in entity["campos"]:
            optional = "" if field["obligatorio"] else "?"
            nullable = "" if field["obligatorio"] else " | null"
            lines.append(f"  {field['apiName']}{optional}: {field['typescript']}{nullable}")
        lines.extend(["}", ""])
    lines.append(
        "export type CrudEntityName = "
        + " | ".join(f"'{entity['nombre']}'" for entity in metadata["entidades"])
        + "\n"
    )
    return "\n".join(lines)


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
    if file_type in {"sql", "java", "ts"}:
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
    initial_migration = root / "db" / "migrations" / "V001__modelo_inicial.sql"
    if initial_migration.exists():
        verificar_cabecera(initial_migration, "sql")
        sql_content = initial_migration.read_text(encoding="utf-8")
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
    frontend_entities = generar_entidades_frontend(modules)
    frontend_entities_content = json.dumps(frontend_entities, ensure_ascii=False, indent=2) + "\n"
    frontend_types_content = generar_tipos_frontend(frontend_entities)
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
        (
            root / "frontend" / "src" / "generated" / "entidades.json",
            frontend_entities_content,
            "json",
        ),
        (
            root / "frontend" / "src" / "generated" / "entidades.ts",
            frontend_types_content,
            "ts",
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
