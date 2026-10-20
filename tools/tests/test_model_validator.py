"""Pruebas de validación del esquema y los módulos JSON del modelo."""

from __future__ import annotations

import json
import shutil
import subprocess
import sys
from pathlib import Path

from tools.model_validator import validar_modelo

ROOT = Path(__file__).resolve().parents[2]
MODEL_DIR = ROOT / "db" / "model" / "modulos"
SCHEMA_PATH = ROOT / "db" / "model" / "model.schema.json"


def copiar_modelo(destino: Path) -> tuple[Path, Path]:
    module_dir = destino / "modulos"
    module_dir.mkdir(parents=True)
    for source in MODEL_DIR.glob("*.json"):
        shutil.copy(source, module_dir / source.name)
    schema = destino / "model.schema.json"
    shutil.copy(SCHEMA_PATH, schema)
    return module_dir, schema


def cargar_modulo(module_dir: Path, name: str) -> tuple[Path, dict]:
    path = module_dir / f"{name}.json"
    return path, json.loads(path.read_text(encoding="utf-8"))


def guardar_modulo(path: Path, module: dict) -> None:
    path.write_text(json.dumps(module, ensure_ascii=False, indent=2), encoding="utf-8")


def test_modelo_del_proyecto_es_valido() -> None:
    errors, entity_count = validar_modelo(MODEL_DIR, SCHEMA_PATH)

    assert errors == []
    assert entity_count >= 30


def test_detecta_referencia_a_entidad_inexistente(tmp_path: Path) -> None:
    module_dir, schema = copiar_modelo(tmp_path)
    path, module = cargar_modulo(module_dir, "seguridad")
    reference = module["entidades"][-1]["campos"][1]["ref"]
    reference["entidad"] = "entidad_inexistente"
    guardar_modulo(path, module)

    errors, _ = validar_modelo(module_dir, schema)

    assert any("referencia inexistente" in error for error in errors)


def test_detecta_tipo_de_campo_no_admitido_por_el_schema(tmp_path: Path) -> None:
    module_dir, schema = copiar_modelo(tmp_path)
    path, module = cargar_modulo(module_dir, "catalogo")
    module["entidades"][0]["campos"][0]["tipo"] = "flotante"
    guardar_modulo(path, module)

    errors, _ = validar_modelo(module_dir, schema)

    assert any("flotante" in error for error in errors)


def test_detecta_nombres_de_entidad_duplicados_entre_modulos(tmp_path: Path) -> None:
    module_dir, schema = copiar_modelo(tmp_path)
    path, module = cargar_modulo(module_dir, "terceros")
    module["entidades"][0]["nombre"] = "producto"
    guardar_modulo(path, module)

    errors, _ = validar_modelo(module_dir, schema)

    assert any("entidad duplicada 'producto'" in error for error in errors)


def test_detecta_campos_duplicados(tmp_path: Path) -> None:
    module_dir, schema = copiar_modelo(tmp_path)
    path, module = cargar_modulo(module_dir, "catalogo")
    entity = module["entidades"][0]
    entity["campos"].append(dict(entity["campos"][0]))
    guardar_modulo(path, module)

    errors, _ = validar_modelo(module_dir, schema)

    assert any("campo duplicado 'id'" in error for error in errors)


def test_rechaza_referencia_a_campo_no_unico(tmp_path: Path) -> None:
    module_dir, schema = copiar_modelo(tmp_path)
    path, module = cargar_modulo(module_dir, "seguridad")
    language_id = module["entidades"][0]["campos"][4]
    language_id["ref"] = {"entidad": "pais", "campo": "nombre"}
    guardar_modulo(path, module)

    errors, _ = validar_modelo(module_dir, schema)

    assert any("no es único" in error for error in errors)


def test_valida_el_comando_cli_y_escribe_log(tmp_path: Path) -> None:
    module_dir, schema = copiar_modelo(tmp_path)
    log_dir = tmp_path / "logs"
    result = subprocess.run(
        [
            sys.executable,
            str(ROOT / "tools" / "cli.py"),
            "validar",
            "--dry-run",
            "--model-dir",
            str(module_dir),
            "--schema",
            str(schema),
            "--log-dir",
            str(log_dir),
        ],
        cwd=ROOT,
        capture_output=True,
        check=False,
        text=True,
    )

    assert result.returncode == 0, result.stderr
    assert "Modelo válido:" in result.stdout
    logs = list(log_dir.glob("validar-*.log"))
    assert len(logs) == 1
    assert "Modelo válido:" in logs[0].read_text(encoding="utf-8")


def test_cli_falla_con_error_y_no_lo_oculta(tmp_path: Path) -> None:
    module_dir, schema = copiar_modelo(tmp_path)
    path, module = cargar_modulo(module_dir, "parametrizacion")
    module["entidades"][0]["campos"][0]["tipo"] = "flotante"
    guardar_modulo(path, module)

    result = subprocess.run(
        [
            sys.executable,
            str(ROOT / "tools" / "cli.py"),
            "validar",
            "--model-dir",
            str(module_dir),
            "--schema",
            str(schema),
            "--log-dir",
            str(tmp_path / "logs"),
        ],
        cwd=ROOT,
        capture_output=True,
        check=False,
        text=True,
    )

    assert result.returncode == 1
    assert "ERROR:" in result.stderr
    assert "Validación fallida" in result.stderr
