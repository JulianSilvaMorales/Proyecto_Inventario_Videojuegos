"""Pruebas de generación determinista y protección de artefactos."""

from __future__ import annotations

import json
import shutil
from pathlib import Path

import pytest

from tools.sql_generator import (
    GENERATED_HEADER,
    GeneratedFileError,
    cargar_modulos,
    crear_schema_resuelto,
    generar_archivos,
    generar_catalogo_crud_java,
    generar_crud_java,
    generar_entidades_frontend,
    generar_inventario_crud_java,
    generar_sql,
    generar_tipos_frontend,
)

ROOT = Path(__file__).resolve().parents[2]


def copiar_entrada_generador(destination: Path) -> Path:
    model_dir = destination / "db" / "model"
    shutil.copytree(ROOT / "db" / "model" / "modulos", model_dir / "modulos")
    shutil.copy(ROOT / "db" / "model" / "model.schema.json", model_dir)
    shutil.copytree(ROOT / "tools" / "templates", destination / "tools" / "templates")
    return destination


def test_generacion_sql_incluye_integridad_y_respeta_dependencias() -> None:
    modules = cargar_modulos(ROOT / "db" / "model" / "modulos")
    sql = generar_sql(modules, ROOT / "tools" / "templates")

    assert sql.startswith(f"-- {GENERATED_HEADER}")
    assert sql.index('CREATE TABLE IF NOT EXISTS public."moneda"') < sql.index(
        'CREATE TABLE IF NOT EXISTS public."pais"'
    )
    assert sql.index('CREATE TABLE IF NOT EXISTS public."idioma"') < sql.index(
        'CREATE TABLE IF NOT EXISTS public."producto_traduccion"'
    )
    assert "JSONB NOT NULL" in sql
    assert "NUMERIC(19,4)" in sql
    assert 'CHECK ("cantidad" >= 0)' in sql
    assert "FOREIGN KEY" in sql
    assert "CREATE TRIGGER" in sql
    assert "rechazar_cambio_inmutable" in sql
    assert "rechazar_eliminacion_producto" in sql
    assert sql.count("CREATE TABLE IF NOT EXISTS public.") == 33


def test_schema_resuelto_conserva_cabecera_y_modulo_de_cada_entidad() -> None:
    modules = cargar_modulos(ROOT / "db" / "model" / "modulos")

    resolved = crear_schema_resuelto(modules)

    assert resolved["_cabecera"] == GENERATED_HEADER
    assert len(resolved["entidades"]) == 33
    assert all("modulo" in entity for entity in resolved["entidades"])


def test_metadatos_frontend_generan_campos_validaciones_permisos_y_tipos() -> None:
    modules = cargar_modulos(ROOT / "db" / "model" / "modulos")

    metadata = generar_entidades_frontend(modules)
    typescript = generar_tipos_frontend(metadata)
    entities = {entity["nombre"]: entity for entity in metadata["entidades"]}
    country_fields = {field["nombre"]: field for field in entities["pais"]["campos"]}
    warehouse_fields = {field["nombre"]: field for field in entities["bodega"]["campos"]}
    customer_fields = {field["nombre"]: field for field in entities["cliente"]["campos"]}
    supplier_fields = {field["nombre"]: field for field in entities["proveedor"]["campos"]}

    assert metadata["_cabecera"] == GENERATED_HEADER
    assert set(entities) == {
        "pais",
        "moneda",
        "idioma",
        "tipo_cambio",
        "impuesto",
        "categoria",
        "bodega",
        "proveedor",
        "cliente",
    }
    assert entities["pais"]["rutaApi"] == "/api/v1/configuracion/paises"
    assert entities["cliente"]["permisosGestion"] == ["CLIENTES_GESTIONAR"]
    assert country_fields["codigo_iso"]["etiquetas"] == {
        "es": "Código ISO",
        "en": "ISO code",
    }
    assert country_fields["habilitado"]["tipo"] == "booleano"
    assert warehouse_fields["pais_id"]["obligatorio"]
    assert customer_fields["consentimiento_en_utc"]["soloLectura"]
    assert supplier_fields["activo"]["default"] is True
    assert "export interface TipoCambio" in typescript
    assert "export type CrudEntityName" in typescript


def test_crud_java_se_genera_desde_el_modelo_de_parametrizacion() -> None:
    modules = cargar_modulos(ROOT / "db" / "model" / "modulos")

    generated = generar_crud_java(modules)

    assert generated.startswith(f"// {GENERATED_HEADER}")
    for entity in ("PAIS", "MONEDA", "IDIOMA", "TIPO_CAMBIO", "IMPUESTO"):
        for operation in ("COLUMNS", "SELECT_BY_ID", "INSERT", "UPDATE", "DELETE"):
            assert f"{entity}_{operation}" in generated
    assert 'INSERT INTO public.\\"pais\\"' in generated
    assert 'UPDATE public.\\"impuesto\\"' in generated


def test_generador_crud_exige_que_las_entidades_conserven_generar_crud() -> None:
    modules = cargar_modulos(ROOT / "db" / "model" / "modulos")
    parametrizacion = next(module for module in modules if module["modulo"] == "parametrizacion")
    pais = next(entity for entity in parametrizacion["entidades"] if entity["nombre"] == "pais")
    pais["generar_crud"] = False

    with pytest.raises(ValueError, match="generar_crud=true"):
        generar_crud_java(modules)


def test_crud_java_de_catalogo_respeta_solo_desactivar_producto() -> None:
    modules = cargar_modulos(ROOT / "db" / "model" / "modulos")

    generated = generar_catalogo_crud_java(modules)

    assert generated.startswith(f"// {GENERATED_HEADER}")
    for entity in ("CATEGORIA", "TIPO_PRODUCTO", "ATRIBUTO_DEFINICION"):
        for operation in ("COLUMNS", "SELECT_BY_ID", "INSERT", "UPDATE", "DELETE"):
            assert f"{entity}_{operation}" in generated
    for operation in ("COLUMNS", "SELECT_BY_ID", "INSERT", "UPDATE"):
        assert f"PRODUCTO_{operation}" in generated
    assert "public static final String PRODUCTO_DELETE" not in generated
    assert 'INSERT INTO public.\\"producto\\"' in generated


def test_crud_java_de_inventario_no_genera_borrado_de_bodega() -> None:
    modules = cargar_modulos(ROOT / "db" / "model" / "modulos")

    generated = generar_inventario_crud_java(modules)

    assert generated.startswith(f"// {GENERATED_HEADER}")
    for operation in ("COLUMNS", "SELECT_BY_ID", "INSERT", "UPDATE"):
        assert f"BODEGA_{operation}" in generated
    assert "public static final String BODEGA_DELETE" not in generated


def test_generar_es_idempotente_y_escribe_archivos_con_cabecera(tmp_path: Path) -> None:
    project = copiar_entrada_generador(tmp_path / "project")

    outputs = generar_archivos(project)
    first_contents = [path.read_text(encoding="utf-8") for path in outputs]
    second_outputs = generar_archivos(project)
    second_contents = [path.read_text(encoding="utf-8") for path in second_outputs]
    resolved = json.loads(outputs[1].read_text(encoding="utf-8"))
    crud_java = outputs[2].read_text(encoding="utf-8")
    catalog_crud_java = outputs[3].read_text(encoding="utf-8")
    inventory_crud_java = outputs[4].read_text(encoding="utf-8")

    assert outputs == second_outputs
    assert first_contents == second_contents
    assert first_contents[0].startswith(f"-- {GENERATED_HEADER}")
    assert resolved["_cabecera"] == GENERATED_HEADER
    assert crud_java.startswith(f"// {GENERATED_HEADER}")
    assert catalog_crud_java.startswith(f"// {GENERATED_HEADER}")
    assert inventory_crud_java.startswith(f"// {GENERATED_HEADER}")


def test_generador_conserva_migracion_inicial_al_cambiar_el_modelo(
    tmp_path: Path,
) -> None:
    project = copiar_entrada_generador(tmp_path / "project")
    model_path = project / "db" / "model" / "modulos" / "inventario.json"
    model = json.loads(model_path.read_text(encoding="utf-8"))
    bodega = next(entity for entity in model["entidades"] if entity["nombre"] == "bodega")
    campo_pais = next(field for field in bodega["campos"] if field["nombre"] == "pais_id")
    bodega["campos"].remove(campo_pais)
    model_path.write_text(json.dumps(model, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    outputs = generar_archivos(project)
    initial_migration = outputs[0]
    baseline = initial_migration.read_text(encoding="utf-8")

    bodega["campos"].append(campo_pais)
    model_path.write_text(json.dumps(model, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    generar_archivos(project)

    assert initial_migration.read_text(encoding="utf-8") == baseline
    generated_inventory = outputs[4].read_text(encoding="utf-8")
    assert '\\"pais_id\\"' in generated_inventory


def test_dry_run_no_crea_ni_modifica_archivos(tmp_path: Path) -> None:
    project = copiar_entrada_generador(tmp_path / "project")

    outputs = generar_archivos(project, dry_run=True)

    assert all(not path.exists() for path in outputs)


def test_generador_se_rehusa_a_sobrescribir_archivo_sin_cabecera(
    tmp_path: Path,
) -> None:
    project = copiar_entrada_generador(tmp_path / "project")
    migration = project / "db" / "migrations" / "V001__modelo_inicial.sql"
    migration.parent.mkdir(parents=True)
    migration.write_text("-- archivo editado manualmente\n", encoding="utf-8")

    with pytest.raises(GeneratedFileError, match="falta la cabecera"):
        generar_archivos(project)

    assert migration.read_text(encoding="utf-8") == "-- archivo editado manualmente\n"


def test_generador_se_rehusa_a_sobrescribir_crud_java_manual(tmp_path: Path) -> None:
    project = copiar_entrada_generador(tmp_path / "project")
    generated = (
        project
        / "backend"
        / "src"
        / "main"
        / "java"
        / "com"
        / "inventario"
        / "generated"
        / "ParametrizacionCrudSql.java"
    )
    generated.parent.mkdir(parents=True)
    generated.write_text("// archivo editado manualmente\n", encoding="utf-8")

    with pytest.raises(GeneratedFileError, match="falta la cabecera"):
        generar_archivos(project)

    assert generated.read_text(encoding="utf-8") == "// archivo editado manualmente\n"


def test_generador_se_rehusa_a_sobrescribir_crud_catalogo_manual(
    tmp_path: Path,
) -> None:
    project = copiar_entrada_generador(tmp_path / "project")
    generated = (
        project
        / "backend"
        / "src"
        / "main"
        / "java"
        / "com"
        / "inventario"
        / "generated"
        / "CatalogoCrudSql.java"
    )
    generated.parent.mkdir(parents=True)
    generated.write_text("// archivo editado manualmente\n", encoding="utf-8")

    with pytest.raises(GeneratedFileError, match="falta la cabecera"):
        generar_archivos(project)

    assert generated.read_text(encoding="utf-8") == "// archivo editado manualmente\n"
