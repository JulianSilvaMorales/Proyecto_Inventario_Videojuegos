# syntax=docker/dockerfile:1
# Crea la base de datos con los scripts de Python (crear-bd): crea la base si
# falta, aplica migraciones pendientes, carga semillas y verifica. Es idempotente,
# por lo que puede ejecutarse en cada arranque sin duplicar datos.
# Contexto de construcción: raíz del repositorio.

FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PYTHONIOENCODING=utf-8
WORKDIR /app
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
COPY tools tools
COPY db db
RUN useradd --system --no-create-home app && mkdir -p logs/tools && chown -R app logs
USER app
CMD ["python", "tools/cli.py", "crear-bd"]
