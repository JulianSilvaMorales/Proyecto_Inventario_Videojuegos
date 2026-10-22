from __future__ import annotations

import copy
from pathlib import Path

import pytest

from tools.cli import main
from tools.demo_seed import Cargador, cargar_datos, validar_datos

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


class ApiFalsa:
    """Responde las consultas del cargador; las órdenes se cuentan por proveedor."""

    def __init__(self, ordenes_por_proveedor: dict[str, int]):
        self.ordenes_por_proveedor = ordenes_por_proveedor

    def get(self, ruta: str) -> object:
        if ruta.startswith("/api/v1/referencias/"):
            return []
        if ruta.startswith("/api/v1/compras/ordenes"):
            proveedor = ruta.split("proveedorId=")[1].split("&")[0]
            return {"totalElements": self.ordenes_por_proveedor.get(proveedor, 0)}
        raise AssertionError(f"Consulta inesperada: {ruta}")


def _cargador(ordenes_por_proveedor: dict[str, int]) -> Cargador:
    cargador = Cargador("http://api", ApiFalsa(ordenes_por_proveedor), {}, "x" * 8, print)
    cargador.proveedores = {"andina": "prov-andina", "atlantic": "prov-atlantic"}
    return cargador


def test_ordenes_de_otros_proveedores_no_impiden_cargar_las_operaciones() -> None:
    # En Render, la E2E crea órdenes con su propio proveedor antes de la demo.
    assert not _cargador({"prov-e2e": 2}).operaciones_ya_cargadas()


def test_ordenes_de_un_proveedor_de_la_demo_evitan_duplicar_las_operaciones() -> None:
    assert _cargador({"prov-e2e": 2, "prov-atlantic": 1}).operaciones_ya_cargadas()
