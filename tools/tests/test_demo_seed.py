from __future__ import annotations

import copy
from pathlib import Path

import pytest

from tools.cli import main
from tools.demo_seed import cargar_datos, validar_datos

ROOT = Path(__file__).resolve().parents[2]


def test_datos_de_demostracion_son_coherentes() -> None:
    datos = cargar_datos(ROOT)

    assert validar_datos(datos) == []
    assert {u["rol"] for u in datos["usuarios"]} == {
        "ENCARGADO_INVENTARIO",
        "EXPERTO_TECNICO",
        "VENDEDOR_CAJERO",
        "GERENTE_PROPIETARIO",
    }
    assert {p["tipo"] for p in datos["productos"]} == {t["codigo"] for t in datos["tipos_producto"]}
    assert {v["pais"] for v in datos["ventas"]} == {"CO", "US"}


def test_datos_de_demostracion_no_guardan_contrasenas() -> None:
    texto = (ROOT / "db" / "seeds" / "demo.json").read_text(encoding="utf-8").lower()
    assert "contrasena" not in texto
    assert "password" not in texto


def test_validacion_detecta_referencias_rotas() -> None:
    datos = copy.deepcopy(cargar_datos(ROOT))
    datos["compras"][0]["lineas"][0]["producto"] = "NO_EXISTE"
    datos["ventas"][0]["vendedor"] = "nadie@pixelvault.example"

    errores = validar_datos(datos)

    assert any("NO_EXISTE" in error for error in errores)
    assert any("vendedor o cliente inexistente" in error for error in errores)


def test_cli_sembrar_demo_dry_run_no_conecta(capsys: pytest.CaptureFixture[str]) -> None:
    assert main(["sembrar-demo", "--dry-run", "--api", "http://127.0.0.1:1"]) == 0
    assert "No se conectó a nada" in capsys.readouterr().out
