from __future__ import annotations

from typing import Any

import pytest

from tools.cli import main
from tools.verificar_roles import ACCESOS, PERMISOS, ROLES, USUARIOS_DEMO, verificar_roles

ADMIN = ("admin-privado@empresa.invalid", "clave-admin")


def _servidor(extra: dict[str, set[str]] | None = None):
    """Simula un backend que cumple el Anexo D; `extra` concede accesos indebidos."""
    extra = extra or {}
    correo_a_rol = {ADMIN[0]: "ADMINISTRADOR", **{c: r for r, c in USUARIOS_DEMO.items()}}

    def pedir(ruta: str, cuerpo: Any = None, token: str | None = None) -> tuple[int, Any]:
        if ruta == "/api/v1/auth/login":
            rol = correo_a_rol[cuerpo["correo"]]
            permisos = {p for _, p, roles in PERMISOS if rol in roles}
            return 200, {"token": rol, "usuario": {"roles": [rol], "permisos": sorted(permisos)}}
        for _, ruta_acceso, roles in ACCESOS:
            if ruta == ruta_acceso:
                permitido = token in roles or token in extra.get(ruta, set())
                return (200 if permitido else 403), None
        raise AssertionError(ruta)

    return pedir


def test_un_backend_que_cumple_el_anexo_d_no_tiene_diferencias() -> None:
    informe, diferencias = verificar_roles("http://api", ADMIN, "demo", _servidor())

    assert diferencias == 0
    assert "todo coincide con el Anexo D" in informe
    assert all(rol in informe for rol in ROLES)
    # El correo del administrador es privado de cada entorno.
    assert ADMIN[0] not in informe


def test_un_acceso_indebido_se_reporta() -> None:
    ruta_proveedores = next(ruta for nombre, ruta, _ in ACCESOS if nombre == "Proveedores")
    pedir = _servidor({ruta_proveedores: {"VENDEDOR_CAJERO"}})

    informe, diferencias = verificar_roles("http://api", ADMIN, "demo", pedir)

    assert diferencias == 1
    assert "❌ 200" in informe


def test_cli_verificar_roles_dry_run_no_conecta(
    monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    monkeypatch.setenv("ADMIN_EMAIL", ADMIN[0])
    monkeypatch.setenv("ADMIN_PASSWORD", ADMIN[1])
    monkeypatch.setenv("DEMO_PASSWORD", "demo-clave")

    assert main(["verificar-roles", "--dry-run", "--api", "http://127.0.0.1:1"]) == 0
    assert "no se conectó" in capsys.readouterr().out
