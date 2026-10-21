from __future__ import annotations

from pathlib import Path

import pytest

from tools.cli import main
from tools.test_runner import Paso, ResultadoSuite, contar_junit, escribir_resumen, probar


def test_cuenta_solo_las_suites_hoja_de_los_reportes_junit(tmp_path: Path) -> None:
    (tmp_path / "agregado.xml").write_text(
        '<testsuites tests="5" failures="1">'
        '<testsuite name="a" tests="3" failures="1" errors="0" skipped="1"/>'
        '<testsuite name="b" tests="2" failures="0" errors="1" skipped="0"/>'
        "</testsuites>",
        encoding="utf-8",
    )
    (tmp_path / "simple.xml").write_text(
        '<testsuite name="c" tests="4" failures="0" errors="0" skipped="0"/>', encoding="utf-8"
    )
    (tmp_path / "roto.xml").write_text("<testsuite", encoding="utf-8")

    assert contar_junit(tmp_path) == (9, 1, 1, 1)


def test_resumen_lista_cada_suite_y_marca_los_fallos(tmp_path: Path) -> None:
    correcta = ResultadoSuite("python", [Paso("pytest", 0, 3.2)], pruebas=42, segundos=3.2)
    fallida = ResultadoSuite("e2e", [Paso("playwright", 1, 30.0)], pruebas=1, errores=1)

    resumen = escribir_resumen(tmp_path, [correcta, fallida], tmp_path).read_text(encoding="utf-8")

    assert "Estado general: **FALLIDO**" in resumen
    assert "| python | ✅ correcto | 42 | 0 | 0 | 0 | 3 s | [python.log](python.log) |" in resumen
    assert "| e2e | ❌ fallido | 1 | 0 | 1 | 0 |" in resumen
    assert "- playwright: ❌ (código 1)" in resumen


def test_resumen_se_publica_en_github_actions(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    resumen_ci = tmp_path / "step-summary.md"
    monkeypatch.setenv("GITHUB_STEP_SUMMARY", str(resumen_ci))
    correcta = ResultadoSuite("backend", [Paso("mvn", 0, 1.0)], pruebas=73)

    escribir_resumen(tmp_path, [correcta], tmp_path)

    assert "| backend | ✅ correcto | 73 |" in resumen_ci.read_text(encoding="utf-8")


def test_una_suite_sin_pasos_no_cuenta_como_correcta() -> None:
    assert not ResultadoSuite("e2e").correcta


def test_probar_rechaza_suites_desconocidas(tmp_path: Path) -> None:
    with pytest.raises(ValueError, match="Suites desconocidas: movil"):
        probar(tmp_path, ["python", "movil"])


def test_probar_dry_run_no_crea_logs(tmp_path: Path, capsys: pytest.CaptureFixture[str]) -> None:
    assert probar(tmp_path, ["python", "e2e"], dry_run=True) == (0, None)
    assert not (tmp_path / "logs").exists()
    assert "python, e2e" in capsys.readouterr().out


def test_cli_probar_dry_run(capsys: pytest.CaptureFixture[str]) -> None:
    assert main(["probar", "--suites", "python,backend", "--dry-run"]) == 0
    assert "python, backend" in capsys.readouterr().out
