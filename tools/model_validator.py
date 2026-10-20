"""Validación estructural y de referencias del modelo JSON del dominio."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from jsonschema import Draft202012Validator
from jsonschema.exceptions import SchemaError


def _ruta_json(path: list[Any]) -> str:
    """Representa una ubicación JSON legible para mensajes de validación."""
    result = "$"
    for part in path:
        if isinstance(part, int):
            result += f"[{part}]"
        else:
            result += f".{part}"
    return result


def _leer_json(path: Path, errors: list[str]) -> Any | None:
    try:
        with path.open(encoding="utf-8") as model_file:
            return json.load(model_file)
    except OSError as exc:
        errors.append(f"{path}: no se pudo leer el archivo ({exc}).")
    except json.JSONDecodeError as exc:
        errors.append(f"{path}:{exc.lineno}:{exc.colno}: JSON inválido: {exc.msg}.")
    return None


def validar_modelo(model_dir: Path, schema_path: Path) -> tuple[list[str], int]:
    """Valida los módulos del modelo y devuelve errores y cantidad de entidades."""
    errors: list[str] = []
    schema = _leer_json(schema_path, errors)
    if schema is None:
        return errors, 0

    try:
        Draft202012Validator.check_schema(schema)
    except SchemaError as exc:
        return [f"{schema_path}: JSON Schema inválido: {exc.message}."], 0

    schema_validator = Draft202012Validator(schema)
    module_paths = sorted(model_dir.glob("*.json"))
    if not module_paths:
        return [f"{model_dir}: no se encontraron módulos JSON del modelo."], 0

    modules: list[tuple[Path, dict[str, Any]]] = []
    module_names: set[str] = set()
    entities: dict[str, dict[str, Any]] = {}
    entity_sources: dict[str, Path] = {}

    for module_path in module_paths:
        module = _leer_json(module_path, errors)
        if module is None:
            continue

        validation_errors = sorted(
            schema_validator.iter_errors(module),
            key=lambda error: (list(error.absolute_path), error.message),
        )
        for error in validation_errors:
            errors.append(
                f"{module_path}:{_ruta_json(list(error.absolute_path))}: {error.message}."
            )
        if validation_errors:
            continue

        module_name = module["modulo"]
        if module_name in module_names:
            errors.append(f"{module_path}: módulo duplicado '{module_name}'.")
        module_names.add(module_name)
        modules.append((module_path, module))

        for entity in module["entidades"]:
            entity_name = entity["nombre"]
            if entity_name in entities:
                errors.append(
                    f"{module_path}: entidad duplicada '{entity_name}' "
                    f"(también definida en {entity_sources[entity_name]})."
                )
                continue
            entities[entity_name] = entity
            entity_sources[entity_name] = module_path

    for module_path, module in modules:
        for entity in module["entidades"]:
            entity_name = entity["nombre"]
            if entity_name not in entities or entities[entity_name] is not entity:
                continue
            _validar_entidad(module_path, entity, entities, errors)

    for required_name in ("movimiento_inventario", "auditoria"):
        entity = entities.get(required_name)
        if entity is not None and (not entity["inmutable"] or entity["generar_crud"]):
            errors.append(
                f"{entity_sources[required_name]}: '{required_name}' debe ser "
                "inmutable y no debe generar CRUD."
            )

    return errors, len(entities)


def _validar_entidad(
    module_path: Path,
    entity: dict[str, Any],
    entities: dict[str, dict[str, Any]],
    errors: list[str],
) -> None:
    entity_name = entity["nombre"]
    fields = entity["campos"]
    field_by_name: dict[str, dict[str, Any]] = {}

    if entity["inmutable"] and entity["generar_crud"]:
        errors.append(f"{module_path}: '{entity_name}' es inmutable y no puede generar CRUD.")
    if entity.get("solo_desactivar") and entity["inmutable"]:
        errors.append(
            f"{module_path}: '{entity_name}' no puede ser inmutable y permitir "
            "desactivación a la vez."
        )

    primary_keys = []
    for field in fields:
        field_name = field["nombre"]
        if field_name in field_by_name:
            errors.append(
                f"{module_path}: campo duplicado '{field_name}' en entidad '{entity_name}'."
            )
            continue
        field_by_name[field_name] = field
        if field.get("pk", False):
            primary_keys.append(field)

        if "min" in field and "max" in field and field["min"] > field["max"]:
            errors.append(f"{module_path}: rango min/max inválido en '{entity_name}.{field_name}'.")
        if field["tipo"] == "decimal":
            if "precision" not in field or "escala" not in field:
                errors.append(
                    f"{module_path}: '{entity_name}.{field_name}' de tipo decimal "
                    "requiere precision y escala."
                )
            elif field["escala"] > field["precision"]:
                errors.append(
                    f"{module_path}: escala mayor que precision en '{entity_name}.{field_name}'."
                )
        elif "precision" in field or "escala" in field:
            errors.append(
                f"{module_path}: precision y escala solo aplican a campos "
                f"decimales ('{entity_name}.{field_name}')."
            )
        if "valores" in field and field["tipo"] != "texto":
            errors.append(
                f"{module_path}: 'valores' solo aplica a campos de texto "
                f"('{entity_name}.{field_name}')."
            )
        if "ref" in field and field["tipo"] != "uuid":
            errors.append(
                f"{module_path}: la referencia '{entity_name}.{field_name}' debe ser de tipo uuid."
            )

    if len(primary_keys) != 1:
        errors.append(
            f"{module_path}: '{entity_name}' debe definir exactamente una clave "
            f"primaria; se encontraron {len(primary_keys)}."
        )
    elif primary_keys[0]["nombre"] != "id" or primary_keys[0]["tipo"] != "uuid":
        errors.append(
            f"{module_path}: la clave primaria de '{entity_name}' debe ser "
            "un campo uuid llamado 'id'."
        )
    if entity.get("solo_desactivar"):
        active_field = field_by_name.get("activo")
        if active_field is None or active_field["tipo"] != "booleano":
            errors.append(
                f"{module_path}: '{entity_name}' con solo_desactivar requiere "
                "un campo booleano 'activo'."
            )

    for constraint_name in ("indices", "unicos"):
        for field_group in entity.get(constraint_name, []):
            unknown_fields = set(field_group) - field_by_name.keys()
            if unknown_fields:
                errors.append(
                    f"{module_path}: {constraint_name} de '{entity_name}' "
                    f"referencia campos inexistentes: {', '.join(sorted(unknown_fields))}."
                )

    for field in fields:
        reference = field.get("ref")
        if reference is None:
            continue
        target = entities.get(reference["entidad"])
        if target is None:
            errors.append(
                f"{module_path}: referencia inexistente "
                f"'{entity_name}.{field['nombre']}' → "
                f"'{reference['entidad']}.{reference['campo']}'."
            )
            continue

        target_fields = {target_field["nombre"]: target_field for target_field in target["campos"]}
        target_field = target_fields.get(reference["campo"])
        if target_field is None:
            errors.append(
                f"{module_path}: campo referenciado inexistente "
                f"'{reference['entidad']}.{reference['campo']}'."
            )
            continue
        if not any(unique_set == {reference["campo"]} for unique_set in unique_sets_for(target)):
            errors.append(
                f"{module_path}: el campo referenciado "
                f"'{reference['entidad']}.{reference['campo']}' no es único."
            )
        if field["tipo"] != target_field["tipo"]:
            errors.append(
                f"{module_path}: tipo incompatible en referencia "
                f"'{entity_name}.{field['nombre']}' ({field['tipo']}) → "
                f"'{reference['entidad']}.{reference['campo']}' "
                f"({target_field['tipo']})."
            )


def unique_sets_for(entity: dict[str, Any]) -> list[set[str]]:
    """Devuelve claves únicas declaradas, incluida la clave primaria."""
    unique_sets = [set(fields) for fields in entity.get("unicos", [])]
    unique_sets.extend({field["nombre"]} for field in entity["campos"] if field.get("pk", False))
    return unique_sets
