"""Carga local opcional de variables desde .env sin reemplazar el entorno."""

from __future__ import annotations

import os
import re
from pathlib import Path


def cargar_env_file(path: Path) -> None:
    """Carga asignaciones KEY=VALUE; los valores del proceso tienen prioridad."""
    if not path.exists():
        return

    for line_number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise ValueError(f"{path}:{line_number}: se esperaba una asignación KEY=VALUE.")

        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip()
        if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", key):
            raise ValueError(f"{path}:{line_number}: nombre de variable inválido.")
        if value.startswith(("'", '"')):
            quote = value[0]
            if len(value) < 2 or value[-1] != quote:
                raise ValueError(f"{path}:{line_number}: valor entre comillas sin cerrar.")
            value = value[1:-1]
        os.environ.setdefault(key, value)
