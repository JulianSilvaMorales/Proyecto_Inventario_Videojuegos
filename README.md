# PixelVault — Sistema de inventario y ventas para consolas y videojuegos

> 🌐 **Aplicación desplegada (Render):** **https://pixelvault-web-ceen.onrender.com**
>
> API pública: https://pixelvault-api-ljq6.onrender.com
> (estado en [`/actuator/health`](https://pixelvault-api-ljq6.onrender.com/actuator/health),
> documentación en [`/swagger-ui`](https://pixelvault-api-ljq6.onrender.com/swagger-ui)).
> El plan gratuito de Render suspende el backend tras 15 minutos sin uso: **la
> primera visita puede tardar alrededor de un minuto** mientras despierta.

**PixelVault** es una aplicación web para una tienda de consolas y videojuegos
que vende en varios países: administra el catálogo, las compras a proveedores,
el inventario por bodega y las ventas en la moneda de cada país, con impuestos,
tipos de cambio y textos configurables (español e inglés).

Se construyó por etapas (E0 a E12) siguiendo [`spec/GUIDE.md`](spec/GUIDE.md),
con los requisitos de [`spec/REQUIREMENTS.md`](spec/REQUIREMENTS.md) y las
reglas de trabajo de [`spec/INSTRUCTIONS.md`](spec/INSTRUCTIONS.md). Este
documento explica **qué se hizo, cómo, con qué herramienta y dónde está cada
resultado**, para que alguien que llega por primera vez pueda instalarlo,
probarlo y seguir el rastro de cada decisión.

> Todos los comandos están escritos para el **Símbolo del sistema de Windows
> (`cmd`)** y se ejecutan desde la carpeta raíz del proyecto, salvo que se
> indique otra. Funcionan igual en PowerShell. Las líneas que empiezan por
> `rem` son comentarios.

## Índice

1. [Qué hace la aplicación](#1-qué-hace-la-aplicación)
2. [Enlaces y usuarios de prueba](#2-enlaces-y-usuarios-de-prueba)
3. [Tecnologías y herramientas](#3-tecnologías-y-herramientas)
4. [Instalación desde cero en otro equipo (`git clone`)](#4-instalación-desde-cero-en-otro-equipo-git-clone)
5. [Cómo está construido](#5-cómo-está-construido)
6. [Qué se hizo, etapa por etapa](#6-qué-se-hizo-etapa-por-etapa)
7. [Pruebas: qué se probó, cómo, con qué y dónde están los resultados](#7-pruebas-qué-se-probó-cómo-con-qué-y-dónde-están-los-resultados)
8. [Despliegue en Render, respaldo y restauración](#8-despliegue-en-render-respaldo-y-restauración)
9. [Integración continua y protección de `main`](#9-integración-continua-y-protección-de-main)
10. [Problemas encontrados y cómo se corrigieron](#10-problemas-encontrados-y-cómo-se-corrigieron)
11. [Ramas, pull requests e historial](#11-ramas-pull-requests-e-historial)
12. [Estructura del proyecto](#12-estructura-del-proyecto)
13. [Decisiones técnicas](#13-decisiones-técnicas)
14. [Cómo corregir a mano](#14-cómo-corregir-a-mano)
15. [Fase 2 y pendientes conocidos](#15-fase-2-y-pendientes-conocidos)

---

## 1. Qué hace la aplicación

| Módulo | Qué permite |
|---|---|
| **Acceso y roles** | Inicio de sesión con JWT; cinco roles (administrador, encargado de inventario, experto técnico, vendedor/cajero y gerente) con menú y acciones según sus permisos; bloqueo tras varios intentos fallidos y cierre por inactividad, ambos configurables; alcance por país (un vendedor de Colombia solo ve Colombia). |
| **Configuración internacional** | Países, monedas, idiomas, tipos de cambio, impuestos por país y parámetros del negocio o de cada país con vigencia (por ejemplo, los medios de pago). Un país y una moneda nuevos se agregan solo con datos, sin programar. |
| **Catálogo** | Productos con atributos según su tipo (consola, videojuego, tarjeta de regalo…), nombre y descripción en español e inglés, y precios por moneda con periodos de vigencia. Los productos se desactivan, nunca se borran. |
| **Compras** | Productos oficiales de cada proveedor y órdenes de compra en la moneda del proveedor: borrador → enviada → recibida parcial o completa, o cancelada, con tipo de cambio hacia la moneda base. |
| **Inventario** | Existencias por bodega, entradas contra órdenes de compra, salidas por pérdida, daño, robo o defecto, ajustes de conteo con aprobación del administrador e historial inmutable de movimientos. |
| **Ventas** | Venta en la moneda local con el impuesto del país, confirmación de pago que descuenta inventario, anulación de ventas pendientes y recibo simulado. |
| **Reportes** | Existencias y movimientos con filtros y exportación a CSV. |
| **Auditoría** | Toda operación de escritura queda registrada (quién, cuándo, antes y después). |
| **Idiomas** | Interfaz en español e inglés; el idioma y los formatos de fecha, número y moneda cambian sin recargar la página. |

## 2. Enlaces y usuarios de prueba

| Qué | Dónde |
|---|---|
| Aplicación pública (Render) | https://pixelvault-web-ceen.onrender.com |
| API pública | https://pixelvault-api-ljq6.onrender.com (salud: `/actuator/health`; documentación: `/swagger-ui`) |
| Aplicación local con Docker | http://localhost:8081 (API en http://localhost:8080) |
| Aplicación en desarrollo | http://localhost:5173 (API en http://localhost:8080) |
| Repositorio y CI | https://github.com/JulianSilvaMorales/Proyecto_Inventario_Videojuegos (pestaña **Actions**) |
| Evidencias de la corrida final | [`docs/evidencias/`](docs/evidencias/README.md) |

**Usuarios de prueba.** Las contraseñas **no están en el repositorio**: se
definen en el archivo `.env` de cada equipo (ver
[4.3](#43-crear-el-archivo-env)) o, en Render, en la consola al crear la base.
Los usuarios de demostración existen después de cargar los datos de
demostración ([4.4](#44-opción-a-ejecutar-todo-con-docker-recomendada), paso 4).

| Rol | Correo | Contraseña | Qué puede hacer |
|---|---|---|---|
| Administrador | El de `ADMIN_EMAIL` | `ADMIN_PASSWORD` | Todo: parametrización, catálogo, compras, inventario, ventas, clientes, aprobación de ajustes y reportes. |
| Encargado de inventario | `encargado.inventario@pixelvault.example` | `DEMO_PASSWORD` | Productos, proveedores, compras y entradas, salidas, solicitud de ajustes operativos y reportes de inventario. |
| Experto técnico | `experto.tecnico@pixelvault.example` | `DEMO_PASSWORD` | Consultar catálogo e inventario y solicitar ajustes técnicos. |
| Vendedor/cajero (Colombia) | `vendedor.bogota@pixelvault.example` | `DEMO_PASSWORD` | Ventas y clientes de Colombia; anular ventas pendientes. |
| Vendedora (Estados Unidos, en inglés) | `ventas.miami@pixelvault.example` | `DEMO_PASSWORD` | Lo mismo, limitado a Estados Unidos. |
| Gerente/propietario | `gerente@pixelvault.example` | `DEMO_PASSWORD` | Consultar catálogo, inventario y proveedores, y todos los reportes. |

La matriz completa de permisos es el Anexo D de [`spec/GUIDE.md`](spec/GUIDE.md).
El comando `python tools/cli.py verificar-roles` la comprueba contra cualquier
backend en ejecución (ver [7.6](#76-verificaciones-de-la-entrega)).

## 3. Tecnologías y herramientas

| Herramienta | Versión | Para qué se usó | Dónde |
|---|---|---|---|
| **Java** + **Spring Boot** | 17 · 3.5.6 | Backend: API REST, seguridad, transacciones y validación del esquema | `backend/` |
| **Maven Wrapper** | Maven 3.9.11 | Compilar y probar el backend sin instalar Maven (`.\mvnw.cmd`) | `backend/mvnw.cmd` |
| **PostgreSQL** | 16 | Base de datos | Docker (`postgres:16-alpine`), local o Render |
| **Python** + Jinja2, jsonschema, psycopg, bcrypt | 3.12 | CLI del proyecto: validar el modelo, generar SQL, crear la base, sembrar datos, respaldar y orquestar las pruebas | `tools/` |
| **React** + **TypeScript** + **Material UI** | 19 · 6 · 9 | Interfaz web | `frontend/src/` |
| **Vite** | 8 | Servidor de desarrollo y compilación del frontend | `frontend/vite.config.ts` |
| **i18next** | 26 | Textos en español e inglés | `frontend/src/i18n/` |
| **pytest** | 8.3.5 | Pruebas de Python | `tools/tests/` |
| **JUnit 5**, **Spring Boot Test**, **MockMvc**, **Testcontainers** | — | Pruebas del backend (unitarias e integración con PostgreSQL desechable) | `backend/src/test/` |
| **Vitest** + **Testing Library** | 5 · 16 | Pruebas del frontend | `frontend/src/**/*.test.ts(x)` |
| **Playwright** (Chromium) | 1.64.1 | Pruebas de extremo a extremo en el navegador | `e2e/` |
| **ruff** · **oxlint** · **tsc** | 0.11.8 · 1.x · 6 | Estilo y errores de Python; lint y tipos del frontend | `pyproject.toml`, `frontend/.oxlintrc.json` |
| **pre-commit** + **detect-secrets** | 4.2.0 · 1.5.0 | Revisiones antes de cada commit y push; bloquea secretos | `.pre-commit-config.yaml` |
| **Docker** + **Docker Compose** | Docker Desktop | Contenedores de base, backend y frontend; base desechable de las pruebas; respaldos | `docker/`, `docker-compose.yml` |
| **nginx** | 1.27 | Servir el frontend compilado dentro de Docker | `docker/nginx.conf` |
| **GitHub Actions** | — | Integración continua: las cuatro suites en cada pull request | `.github/workflows/ci.yml` |
| **Render** | Plan gratuito | Despliegue público de demostración | `render.yaml` |
| **Git** + **GitHub** | — | Control de versiones, pull requests y protección de `main` | — |

## 4. Instalación desde cero en otro equipo (`git clone`)

Hay dos formas de ejecutar el proyecto:

| Opción | Para qué | Qué hay que instalar |
|---|---|---|
| **A. Docker** (recomendada) | Usar o demostrar el sistema completo. | Git y Docker Desktop. |
| **B. Desarrollo local** | Modificar el código con recarga rápida y ejecutar todas las pruebas. | Git, Docker Desktop, Python 3.12+, Java 17, Node.js 22 LTS y PostgreSQL 16. |

### 4.1 Instalar los programas

| Programa | Dónde descargarlo | Comprobar en `cmd` | Opción |
|---|---|---|---|
| Git | https://git-scm.com/download/win | `git --version` | A y B |
| Docker Desktop | https://www.docker.com/products/docker-desktop/ | `docker --version` y `docker compose version` | A y B |
| Python 3.12 o superior | https://www.python.org/downloads/ (marcar *Add python.exe to PATH*) | `py -3 --version` | B |
| Java 17 (Temurin JDK) | https://adoptium.net/temurin/releases/?version=17 | `java -version` | B |
| Node.js 22 LTS (incluye npm) | https://nodejs.org/ | `node --version` y `npm --version` | B |
| PostgreSQL 16 | https://www.postgresql.org/download/windows/ (anotar la contraseña del usuario `postgres`) | `psql --version` | B |

Después de instalar Docker Desktop, **ábrelo y espera a que diga que está en
ejecución** (*Engine running*): lo necesitan los contenedores, las pruebas de
integración y los respaldos.

### 4.2 Clonar el repositorio

Clona en una carpeta de ruta corta (por ejemplo, `C:\proyectos`): Windows
limita las rutas a 260 caracteres y algunas del proyecto miden hasta 99.

```bat
mkdir C:\proyectos
cd C:\proyectos
git clone https://github.com/JulianSilvaMorales/Proyecto_Inventario_Videojuegos.git
cd Proyecto_Inventario_Videojuegos
```

Si de todos modos aparece un error de rutas largas (`Filename too long`),
actívalas en Git y vuelve a clonar:

```bat
git config --global core.longpaths true
```

### 4.3 Crear el archivo `.env`

La configuración y las contraseñas de cada equipo van en un archivo `.env` que
**nunca se sube a GitHub** (está en `.gitignore`). Se crea a partir de la
plantilla `.env.example`:

```bat
copy .env.example .env
notepad .env
```

Completa estos valores (formato `CLAVE=valor`, sin comillas ni espacios
alrededor del `=`):

| Variable | Qué poner | Ejemplo |
|---|---|---|
| `DB_PASSWORD` | Opción A: cualquier contraseña inventada para la base de Docker. Opción B: la contraseña de tu PostgreSQL local. | `ClaveBase2026` |
| `DB_USER` | Opción A: déjalo en `inventario`. Opción B: el usuario de tu PostgreSQL local (normalmente `postgres`). | `inventario` |
| `JWT_SECRET` | Una clave aleatoria en base64 de 32 bytes o más; genérala con el comando de abajo. | `kq3…==` (64 caracteres) |
| `ADMIN_EMAIL` | El correo con el que entrará el administrador. | `admin@miempresa.com` |
| `ADMIN_PASSWORD` | La contraseña del administrador (larga; sin `%` ni `"`). | `Admin-PixelVault-2026` |
| `DEMO_PASSWORD` | La contraseña común de los usuarios de demostración (8 caracteres o más). | `Demo-PixelVault-26` |

Las demás variables ya tienen un valor que funciona. Para generar
`JWT_SECRET`, ejecuta esto en `cmd` y pega en `.env` el texto que imprime:

```bat
powershell -NoProfile -Command "$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)"
```

### 4.4 Opción A: ejecutar todo con Docker (recomendada)

Con Docker Desktop abierto, desde la carpeta del proyecto:

```bat
rem 1. Construir las imágenes y levantar la base, la creación de la base, el backend y el frontend.
rem    La primera vez tarda varios minutos (descarga imágenes y compila); --wait espera a que todo esté sano.
docker compose up --build -d --wait

rem 2. Comprobar que los servicios están arriba (db, backend y frontend "healthy"; db-init "exited (0)" es lo esperado).
docker compose ps -a

rem 3. Comprobar la API: debe responder {"status":"UP"}.
curl http://localhost:8080/actuator/health

rem 4. (Opcional) Cargar los datos de demostración: usuarios por rol, productos, compras, ventas en COP y USD.
docker compose run --rm db-init python tools/cli.py sembrar-demo --api http://backend:8080
```

5. Abre **http://localhost:8081** e inicia sesión con `ADMIN_EMAIL` y
   `ADMIN_PASSWORD`, o con un usuario de demostración y `DEMO_PASSWORD`.

| Para… | Comando |
|---|---|
| Detener (conserva los datos) | `docker compose down` |
| Volver a arrancar | `docker compose up -d --wait` |
| Aplicar cambios del código (después de `git pull`) | `docker compose up --build -d --wait` |
| Ver los logs del backend (Ctrl+C para salir) | `docker compose logs -f backend` |
| Borrar la base y empezar de cero | `docker compose down -v` |

Qué hace cada servicio y por qué `db-init` aparece detenido se explica en
[6.8](#68-e10--contenedores-docker).

### 4.5 Opción B: desarrollo local

**1. Entorno de Python** (una sola vez):

```bat
py -3 -m venv .venv
.venv\Scripts\activate.bat
python -m pip install --upgrade pip
python -m pip install -r requirements-dev.txt
pre-commit install
```

- `.venv` es un entorno aislado de Python para el proyecto. **En cada consola
  nueva** hay que activarlo con `.venv\Scripts\activate.bat` (el indicador
  `(.venv)` aparece al inicio de la línea).
- `requirements-dev.txt` instala las dependencias de ejecución
  (`requirements.txt`) y las de desarrollo (pytest, ruff, pre-commit,
  detect-secrets).
- `pre-commit install` activa las revisiones automáticas antes de cada commit y
  de cada push (ver [9.2](#92-revisiones-locales-antes-de-cada-commit-y-push)).

**2. Base de datos local.** En `.env`, con PostgreSQL 16 instalado:
`DB_HOST=localhost`, `DB_PORT=5432`, `DB_NAME=inventario`, `DB_USER=postgres` y
`DB_PASSWORD=<contraseña de postgres>`. Luego:

```bat
python tools/cli.py crear-bd
```

Crea la base `inventario` si no existe, aplica las migraciones, carga las
semillas (países, monedas, impuestos, roles, permisos, bodega, parámetros y el
administrador) y verifica el esquema. Deja su log en `logs/tools/`.

**3. Backend** (en una consola):

```bat
cd backend
.\mvnw.cmd spring-boot:run
```

Lee el `.env` de la raíz automáticamente. Queda en http://localhost:8080; se
detiene con `Ctrl+C`.

**4. Frontend** (en otra consola):

```bat
cd frontend
npm ci
npm run dev
```

Queda en http://localhost:5173 y se recarga solo al guardar cambios.

**5. (Opcional) Datos de demostración** (en una tercera consola, con el backend
arrancado y el entorno de Python activado):

```bat
python tools/cli.py sembrar-demo
```

### 4.6 Comprobar que todo funciona

Con la opción B instalada y Docker Desktop abierto:

```bat
rem Las cuatro suites de pruebas (unos 4 minutos); resultados en logs\tests\<fecha-hora>\resumen.md
python tools/cli.py probar

rem Todo el ciclo: validar el modelo, generar, crear la base y probar
python tools/cli.py todo

rem Un usuario de cada rol contra el backend en ejecución (requiere los datos de demostración)
python tools/cli.py verificar-roles --api http://localhost:8080
```

Qué ejecuta cada comando y dónde deja sus resultados: [sección 7](#7-pruebas-qué-se-probó-cómo-con-qué-y-dónde-están-los-resultados).

### 4.7 Problemas frecuentes

| Síntoma | Solución |
|---|---|
| `docker compose` dice que falta una variable (`Defina DB_PASSWORD en .env`) | Completa esa variable en `.env` (ver [4.3](#43-crear-el-archivo-env)). |
| `error during connect` o "Docker no está disponible" | Abre Docker Desktop y espera a *Engine running*. |
| El puerto 8080 u 8081 está ocupado | Cambia `BACKEND_PORT` o `FRONTEND_PORT` en `.env` y vuelve a ejecutar `docker compose up --build -d --wait`. |
| `Filename too long` al clonar | `git config --global core.longpaths true` y clona en una ruta corta. |
| El backend se detiene al arrancar avisando que el esquema no coincide con el modelo | Ejecuta `python tools/cli.py crear-bd` (opción B) o `docker compose up -d --wait` (opción A) para aplicar las migraciones. |
| `python` no se reconoce | Activa el entorno: `.venv\Scripts\activate.bat`. |
| La aplicación de Render tarda en abrir | Es normal: el backend gratuito se suspende sin uso; espera alrededor de un minuto. |
| Inicio de sesión bloqueado tras varios intentos | El administrador lo desbloquea con `POST /api/v1/admin/usuarios/{id}/desbloquear` (por ejemplo, desde `/swagger-ui`). El número de intentos es el parámetro `seguridad.intentos_fallidos_max` (Configuración → Parámetros). |

---

## 5. Cómo está construido

```text
 Navegador ──► Frontend (React + MUI, Vite)          frontend/
                 │  HTTP JSON, token JWT
                 ▼
             Backend (Spring Boot, /api/v1)          backend/
               controller → service → repository (JDBC + SQL generado)
                 │
                 ▼
             PostgreSQL 16  ◄── creado SOLO por  python tools/cli.py crear-bd
                 ▲                                  (migraciones + semillas)
                 │
 Modelo JSON (db/model/modulos) ──generar──► migración V001, modelo resuelto,
                                             SQL de CRUD del backend y
                                             metadatos del frontend
```

- **Una sola fuente de verdad:** las tablas se describen en JSON
  (`db/model/modulos/*.json`). El CLI de Python genera a partir de él la
  migración inicial, el modelo resuelto que valida el backend, las sentencias
  SQL de CRUD del backend y los metadatos del CRUD genérico del frontend.
- **La base la crea solo Python** (`crear-bd`); el backend nunca modifica el
  esquema: al arrancar comprueba que coincide con el modelo y, si no, se detiene.
- **Capas del backend:** `controller` (valida y autoriza), `service` (reglas,
  transacciones y auditoría), `repository` (SQL con JDBC), `domain` y `dto`
  (datos internos y contratos de la API).
- **Frontend:** una pantalla por módulo (`components/<módulo>/`), cliente HTTP
  común (`api/`), textos en `i18n/locales/` y formatos con `Intl` (`utils/`).

---

## 6. Qué se hizo, etapa por etapa

Cada etapa se entregó con un informe y se aprobó antes de pasar a la siguiente.

| Etapa | Qué se hizo | Cómo y con qué herramienta | Dónde está | Resultado o evidencia | Estado |
|---|---|---|---|---|---|
| E0 | Estructura del repositorio, configuración local y controles de calidad | Git, pre-commit, ruff, detect-secrets, `.env.example` | Raíz del proyecto | `pre-commit run --all-files` en verde | Aprobada |
| E1 | Análisis de requisitos: entidades, reglas, estados y permisos | Lectura de `spec/REQUIREMENTS.md`; documento en Markdown | `spec/modelo-conceptual.md` | Modelo conceptual revisado | Aprobada |
| E2 | Modelo de datos modular en JSON con su esquema y validador | JSON Schema + Python (`jsonschema`) | `db/model/`, `tools/model_validator.py` | `python tools/cli.py validar` → 33 entidades válidas | Aprobada |
| E3 | Generación de SQL, migraciones con checksum, semillas y CLI de base de datos | Python + Jinja2 + psycopg | `tools/`, `db/migrations/`, `db/seeds/` | `crear-bd` idempotente (pruebas con base real) | Aprobada |
| E4 | Backend base: API `/api/v1`, errores Problem Details, verificación del esquema | Spring Boot 3.5.6 (Java 17), Maven Wrapper | `backend/` | Backend arranca y valida el esquema | Aprobada |
| E5.1–E5.5 | Autenticación y roles; parametrización; catálogo; inventario y compras; ventas y reportes | Spring Boot, Spring Security (JWT), JDBC | `backend/src/main/java/com/inventario/` | Pruebas unitarias y de integración del backend | Aprobada |
| E6.1–E6.4 | Interfaz web: base, CRUD genérico, catálogo, inventario, ventas, reportes y configuración | React 19, TypeScript, Material UI 9, Vite, i18next | `frontend/src/` | Pruebas de Vitest | Aprobada |
| E7 | Verificación: nada de negocio fijo en el código, importes como texto decimal, país y moneda nuevos solo con datos | Revisión del código + prueba de integración | `config/JacksonConfig.java`, `PaisMonedaPorConfiguracionPostgresIntegrationTest` | Prueba en verde | Aprobada |
| E8 | Pruebas con base desechable, E2E con Playwright y comando `probar` con logs | Testcontainers, pytest, Vitest, Playwright, Python | `tools/test_runner.py`, `e2e/` | `logs/tests/<fecha-hora>/resumen.md` | Aprobada |
| E9 | Integración continua y protección de `main` | GitHub Actions, rulesets de GitHub | `.github/workflows/ci.yml` | 4 checks obligatorios en verde | Aprobada |
| E10 | Contenedores, pantalla de Compras, datos de demostración y nueva identidad visual | Docker, Docker Compose, nginx | `docker/`, `docker-compose.yml`, `frontend/src/components/purchases/`, `db/seeds/demo.json` | `docker compose up --build` funcionando; E2E sobre Compose | Aprobada |
| E11 | Despliegue de demostración, respaldo y restauración | Render (Blueprint), `pg_dump`/`pg_restore` en Docker | `render.yaml`, `tools/respaldo.py` | URL públicas; E2E remota en verde ([`docs/evidencias/render/`](docs/evidencias/render/resumen.md)) | Aprobada |
| E12 | Cierre: checklist, evidencias, E2E en USD, verificación de roles y README final | Python, Playwright | `docs/evidencias/`, `tools/verificar_roles.py`, este README | 180 pruebas en verde ([`docs/evidencias/final/`](docs/evidencias/final/resumen.md)) | Finalizada |

### 6.1 E0 y E1 — Base del repositorio y análisis

- **E0:** estructura de carpetas, `.gitignore`, `.env.example` (variables
  documentadas, sin secretos), `pyproject.toml` (configuración de ruff y
  pytest) y `.pre-commit-config.yaml` (revisiones automáticas).
- **E1:** [`spec/modelo-conceptual.md`](spec/modelo-conceptual.md) recoge las
  entidades, relaciones, estados (de compra, venta, pago y ajuste), reglas de
  negocio y la matriz de permisos derivadas de los requisitos. La marca
  **PixelVault** quedó registrada en la especificación, la guía, las
  instrucciones y el modelo conceptual.

### 6.2 E2 y E3 — Modelo de datos, generación de SQL y CLI

**Modelo (E2).** Cada módulo (`catalogo`, `inventario`, `parametrizacion`,
`seguridad`, `terceros`, `transversal` y `ventas`) es un archivo JSON en
`db/model/modulos/` con sus entidades, campos, claves, índices, referencias y
etiquetas en español e inglés. `db/model/model.schema.json` (JSON Schema)
define su formato y `tools/model_validator.py` comprueba además tipos, nombres,
claves e integridad de las referencias.

**Generación y base de datos (E3).** El CLI único `tools/cli.py` reúne todas
las operaciones:

| Comando | Qué hace | Resultado |
|---|---|---|
| `python tools/cli.py validar` | Valida el modelo JSON. | `logs/tools/validar-<fecha>.log` |
| `python tools/cli.py generar` | Genera `db/migrations/V001__modelo_inicial.sql` (solo si no existe), `db/model/schema-resuelto.json`, el SQL de CRUD del backend (`backend/.../generated/`) y los metadatos del frontend (`frontend/src/generated/`). | Archivos generados + log |
| `python tools/cli.py crear-bd` | Crea la base si falta, aplica las migraciones pendientes (registradas con checksum en `migracion_historial`), carga las semillas de `db/seeds/inicial.json` y verifica el esquema. Es idempotente. | Base lista + log |
| `python tools/cli.py sembrar` | Solo carga las semillas iniciales que falten. | Log |
| `python tools/cli.py reiniciar-bd --confirm-db <nombre>` | **Borra y recrea** una base local. Solo permite hosts locales y exige escribir el nombre exacto de la base. | Log |
| `python tools/cli.py sembrar-demo [--api URL]` | Carga los datos de demostración (ver [6.8](#68-e10--contenedores-docker)). | Log |
| `python tools/cli.py respaldar [--podar]` · `restaurar --archivo X --confirm-db <nombre>` | Respaldo y restauración (ver [8.3](#83-respaldo-y-restauración)). | `backups/` + log |
| `python tools/cli.py verificar-roles [--api URL] [--salida archivo.md]` | Comprueba un usuario de cada rol contra el Anexo D (ver [7.6](#76-verificaciones-de-la-entrega)). | Informe + log |
| `python tools/cli.py probar [--suites …] [--e2e-entorno local\|compose\|remoto]` | Ejecuta las pruebas (ver [7](#7-pruebas-qué-se-probó-cómo-con-qué-y-dónde-están-los-resultados)). | `logs/tests/<fecha-hora>/` |
| `python tools/cli.py todo` | `validar` → `generar` → `crear-bd` → `probar`. | Todo lo anterior |

Todos aceptan `--dry-run` (muestra lo que haría sin conectarse ni modificar
nada), `--help` y escriben su log en `logs/tools/<comando>-<fecha>.log`. Los
comandos que usan la base toman la conexión de `DB_HOST`, `DB_PORT`, `DB_NAME`,
`DB_USER` y `DB_PASSWORD` del `.env`, o de `DATABASE_URL` si está definida; las
variables de la consola tienen prioridad sobre el `.env`.

**Migraciones.** `V001__modelo_inicial.sql` se genera desde el modelo y no se
vuelve a modificar (su checksum queda registrado en cada base).
`V002__pais_bodega.sql` agrega el país a las bodegas y
`V003__bodega_pais_obligatorio.sql` lo hace obligatorio (si alguna bodega
existente no tiene país, se detiene con un mensaje). Las tablas de movimientos
de inventario son inmutables (un trigger rechaza `UPDATE` y `DELETE`) y el saldo
nunca puede ser negativo.

**Semillas iniciales** (`db/seeds/inicial.json`): Colombia (COP, IVA 19 %,
habilitado) y Estados Unidos (USD, deshabilitado hasta configurar su impuesto),
español e inglés, los cinco roles con sus permisos, la bodega `GENERAL`, los
parámetros (moneda base `COP`, intentos de inicio de sesión, inactividad, medios
de pago por país…) y el administrador, cuya contraseña se guarda con BCrypt a
partir de `ADMIN_PASSWORD` (nunca en el archivo).

### 6.3 E4 y E5 — Backend

El backend Spring Boot (`backend/`, Java 17) expone la API en `/api/v1`,
responde los errores con *Problem Details* (`application/problem+json`) en el
idioma de la petición y valida el esquema al arrancar (`ddl-auto=validate`).
Lee el `.env` de la raíz (`spring.config.import` en `application.yml`). Perfiles:
`local` (desarrollo) y `produccion` (Docker y Render).

```bat
rem Arrancar (con PostgreSQL y la base creada)
cd backend
.\mvnw.cmd spring-boot:run
rem Compilar y ejecutar sus pruebas (necesita Docker)
.\mvnw.cmd clean verify
```

Rutas útiles: `/actuator/health` (salud), `/swagger-ui` (documentación
interactiva de la API) y `/v3/api-docs` (OpenAPI).

#### E5.1 — Autenticación, roles y sesiones

- `POST /api/v1/auth/login` devuelve un JWT y el usuario con sus roles,
  permisos y países; `POST /api/v1/auth/logout` cierra la sesión.
- Cada petición autenticada renueva el tiempo de inactividad y devuelve el
  token actualizado en la cabecera `X-Auth-Token`.
- Tras `seguridad.intentos_fallidos_max` intentos fallidos la cuenta se
  bloquea; el administrador la desbloquea con
  `POST /api/v1/admin/usuarios/{id}/desbloquear`.
- `JWT_SECRET` (base64, 32 bytes o más) firma los tokens. Las sesiones viven en
  memoria: reiniciar el backend las cierra todas.
- Las respuestas con credenciales o datos usan `Cache-Control: no-store`.

#### E5.2 — Países, monedas, idiomas, tipos de cambio e impuestos

`/api/v1/configuracion/{paises,monedas,idiomas,tipos-cambio,impuestos}`: listas
paginadas (`page`, `size`, `buscar`) y CRUD, con auditoría. Requieren
`PARAMETROS_GESTIONAR` (los tipos de cambio, `TIPOS_CAMBIO_GESTIONAR`).

- Los tipos de cambio son manuales y con vigencia;
  `POST /api/v1/conversiones/moneda` usa la tasa directa o invierte la
  disponible y redondea según la moneda de destino.
- `POST /api/v1/impuestos/calcular` usa el impuesto de la categoría o, si no
  hay, el general del país, incluido o no en el precio.
- Un país solo se habilita con moneda, idioma, zona horaria e impuesto vigente.

#### E5.3 — Catálogo

`/api/v1/catalogo/{categorias,tipos-producto,definiciones-atributos,productos}`.
Lectura con `CATALOGO_CONSULTAR`; gestión con `PRODUCTOS_GESTIONAR`.

- Un producto tiene tipo, categoría y subcategoría activos, atributos según las
  definiciones de su tipo (`TEXTO`, `ENTERO`, `DECIMAL`, `BOOLEANO`, `JSON`, con
  opciones permitidas), traducciones por idioma y precios por moneda con
  vigencia sin superposición. El código interno `PRD-<UUID>` lo asigna el
  sistema.
- Para cambiar un precio se agrega un periodo nuevo; los anteriores no se
  editan. Productos, categorías y tipos se **desactivan** (`DELETE` lógico),
  nunca se borran.

#### E5.4 — Inventario, proveedores y compras

| Recurso | Operaciones |
|---|---|
| `/api/v1/inventario/bodegas` | Consultar, crear y actualizar bodegas con país (el país no cambia después). |
| `/api/v1/proveedores` | Proveedores; vincular productos oficiales (`POST /{id}/productos/{productoId}`) y listarlos (`GET /{id}/productos`). |
| `/api/v1/compras/ordenes` | Crear borrador, enviar, consultar (filtros `estado`, `proveedorId`) y cancelar antes de recibir. |
| `/api/v1/compras/contexto` | Moneda base del negocio (para saber cuándo la tasa es `1`). |
| `/api/v1/compras/entradas` | Recepciones parciales o completas con documento y tasa aplicada. |
| `/api/v1/inventario/saldos` | Existencias por producto, bodega o país. |
| `/api/v1/inventario/salidas` | Pérdidas, daños, robos o defectos con reporte al proveedor oficial. |
| `/api/v1/inventario/movimientos` | Historial inmutable con filtros. |
| `/api/v1/inventario/ajustes` | Solicitar conteos (operativo: encargado; técnico: experto), aprobar, aplicar y revalidar (administrador). |

Reglas: solo se pide a un proveedor activo lo que tiene vinculado; solo se
reciben órdenes `ENVIADA_AL_PROVEEDOR` o `RECIBIDA_PARCIAL`; una recepción no
supera lo pendiente ni repite documento y usa una bodega del país de la orden;
la tasa de la moneda base debe ser `1`. Cada cambio de saldo se serializa por
producto y bodega, crea un movimiento inmutable y escribe auditoría en la misma
transacción. El alcance por país se aplica a bodegas, proveedores, órdenes,
recepciones, ajustes, ventas y reportes.

#### E5.5 — Ventas y reportes

- `POST /api/v1/ventas` crea una venta **pendiente de pago** con país, moneda
  local, cliente opcional y líneas (producto, bodega del mismo país, cantidad);
  guarda precio, impuesto, tipo de cambio e importes calculados, y comprueba
  existencias sin descontarlas.
- `POST /api/v1/ventas/{id}/confirmar` (con `medioPago`) confirma el pago y, en
  una sola transacción, descuenta el inventario, crea los movimientos
  `SALIDA_VENTA` y audita. `POST /{id}/anular` anula una venta pendiente
  (`VENTAS_ANULAR_PENDIENTES`). `GET /{id}/recibo` devuelve el recibo simulado.
- `GET /api/v1/ventas` lista por estado; `GET /api/v1/ventas/paises` da los
  países de venta con su moneda y medios de pago (parámetro
  `ventas.medios_pago`).
- Reportes (`REPORTES_INVENTARIO` o `REPORTES_TODOS`):
  `/api/v1/reportes/inventario/{existencias,movimientos}` paginados y sus
  versiones `.csv` (UTF-8, campos escapados y fórmulas neutralizadas).
- Parámetros: `/api/v1/parametros` (clave, tipo, valor, límites, ámbito
  `NEGOCIO` o `PAIS` y vigencia, sin periodos superpuestos).
- Referencias para formularios: `/api/v1/referencias/{pais,moneda,idioma,categoria,producto,usuario}`,
  con permisos por referencia.

### 6.4 E6 — Interfaz web

```bat
cd frontend
npm ci
npm run dev
```

| Pantalla | Archivo | Qué hace |
|---|---|---|
| Inicio de sesión | `components/LoginPage.tsx` | Correo y contraseña; selector de idioma. |
| Marco y menú | `components/AppShell.tsx`, `navigation.ts` | Menú lateral según los permisos del usuario. |
| Inicio | `components/Dashboard.tsx` | Usuario, roles, permisos y una tarjeta de **formatos de ejemplo** (no son datos reales: muestra cómo cambian importe, número y fecha con el idioma y la moneda). |
| CRUD genérico | `components/CrudPage.tsx` + `frontend/src/generated/` | Tablas, búsqueda y formularios generados desde el modelo para países, monedas, idiomas, tipos de cambio, impuestos, categorías, bodegas, proveedores y clientes. |
| Catálogo | `components/catalog/` | Productos con atributos dinámicos según su tipo, nombres por idioma y precios con vigencia. |
| Inventario | `components/inventory/` | Pestañas de existencias, entradas de compra, salidas, ajustes (solicitud, aprobación, aplicación y revalidación) y movimientos, según el rol. |
| Compras | `components/purchases/` | Órdenes de compra y productos por proveedor (detalle abajo). |
| Ventas | `components/sales/` | Nueva venta con desglose de impuestos, confirmación de pago, anulación y recibo imprimible. |
| Reportes | `components/reports/` | Existencias y movimientos con filtros y descarga CSV. |
| Configuración | `components/settings/` | Parámetros por ámbito y vigencia, y accesos a los CRUD de configuración. |

**Compras** (`components/purchases/`), completada al cerrar E10:

1. *Productos por proveedor*: elegir el proveedor y vincular sus productos
   oficiales (solo ellos se le pueden pedir).
2. *Órdenes de compra → Nueva orden*: el proveedor define la moneda; si es la
   moneda base (COP), el tipo de cambio queda fijo en `1`; si no (por ejemplo,
   USD), se escribe cuántos COP vale 1 USD. Cada línea: producto oficial,
   cantidad y costo unitario (hasta 4 decimales). Se crea en **borrador**.
3. *Detalle de la orden*: **Enviar al proveedor**, **Cancelar orden** (si no
   tiene entradas) o **Ir a Inventario** para registrar la recepción en
   *Inventario → Entradas de compra*.

**Identidad visual:** tema oscuro propio (`theme.ts`: fondo `#0B0D17`, acentos
violeta `#8B5CF6` y cian `#22D3EE`) y logo de una bóveda en *pixel art* 16 × 16
(`PixelVaultLogo.tsx`, también `public/favicon.svg`).

**Textos y formatos:** todos los textos están en `i18n/locales/es.json` y
`en.json`; los importes viajan como texto decimal (`utils/decimal.ts`) y se
muestran con `Intl` (`utils/formatters.ts`) sin pasar por `float`.

### 6.5 E7 — Verificación de parametrización e internacionalización

- **Sin negocio fijo en el código:** se revisaron backend y frontend en busca
  de porcentajes, plazos, monedas, países y medios de pago. Los plazos, intentos
  y la moneda base salen de parámetros; impuestos, tasas, precios, países y
  monedas son datos. Se eliminó la lista fija de monedas del inicio y los
  mensajes fijos en inglés del cliente HTTP.
- **Importes como texto:** `config/JacksonConfig.java` serializa todos los
  `BigDecimal` como texto decimal (por ejemplo `"232.0000"`) y el frontend los
  envía igual.
- **País y moneda nuevos solo con datos:**
  `PaisMonedaPorConfiguracionPostgresIntegrationTest` crea por la API una
  moneda, un país, su impuesto y tipo de cambio, un producto y una bodega, y
  vende con ellos (ver [7.3](#73-detalle-por-suite)).

### 6.6 E8 — Pruebas y logs

Se creó el comando `python tools/cli.py probar`, que ejecuta las cuatro suites
con un PostgreSQL desechable y deja todos los resultados en `logs/tests/`. Todo
el detalle está en la [sección 7](#7-pruebas-qué-se-probó-cómo-con-qué-y-dónde-están-los-resultados).

### 6.7 E9 — Integración continua

GitHub Actions ejecuta las cuatro suites en cada pull request y `main` solo
acepta cambios con todo en verde. Detalle en la [sección 9](#9-integración-continua-y-protección-de-main).

### 6.8 E10 — Contenedores (Docker)

```bat
docker compose up --build -d --wait
```

| Servicio | Imagen (archivo) | Función | Arranca cuando |
|---|---|---|---|
| `db` | `postgres:16-alpine` | Base de datos con volumen persistente `db-data`. | — |
| `db-init` | `docker/db-init.Dockerfile` (Python 3.12) | Ejecuta `python tools/cli.py crear-bd` y termina (código 0). | `db` está sana. |
| `backend` | `docker/backend.Dockerfile` (compila con JDK 17, ejecuta con JRE 21) | API con perfil `produccion` en el puerto `BACKEND_PORT` (8080). | `db-init` terminó bien. |
| `frontend` | `docker/frontend.Dockerfile` (compila con Node 22, sirve con nginx) | Sitio web en el puerto `FRONTEND_PORT` (8081). | `backend` está sano. |

- **¿Por qué `db-init` aparece detenido?** El proyecto exige que la base se
  cree solo con los scripts de Python. `db-init` es ese script empaquetado: crea
  o actualiza la base, verifica el esquema y termina. "Exited (0)" es lo
  correcto; en cada arranque vuelve a correr sin duplicar nada.
- La JVM del backend está ajustada para poca memoria
  (`-XX:MaxRAMPercentage=65 -XX:+UseSerialGC -XX:TieredStopAtLevel=1`).
- `docker/nginx.conf` sirve el frontend con respaldo a `index.html` y caché
  larga para `/assets/`. `.dockerignore` deja fuera `.env`, dependencias,
  compilaciones y logs; `.gitattributes` fuerza saltos de línea LF en los
  archivos que usan los contenedores.
- La pila no tiene reinicio automático: solo corre cuando se levanta con
  `docker compose up`.

#### Datos de demostración

```bat
rem Con Docker (pila arriba)
docker compose run --rm db-init python tools/cli.py sembrar-demo --api http://backend:8080
rem En desarrollo local (backend en http://localhost:8080)
python tools/cli.py sembrar-demo
rem Solo mostrar lo que cargaría
python tools/cli.py sembrar-demo --dry-run
```

Datos en `db/seeds/demo.json`; lógica en `tools/demo_seed.py`. Los usuarios se
insertan en la base (con `DEMO_PASSWORD` cifrada con BCrypt); **todo lo demás
se crea por la API**, con sus reglas, movimientos y auditoría, igual que desde
la interfaz.

| Qué se carga | Detalle |
|---|---|
| Usuarios | Los cinco de la tabla de la [sección 2](#2-enlaces-y-usuarios-de-prueba). |
| Estados Unidos | Impuesto de ejemplo (*Sales tax Florida*, 6 %), país habilitado y tipo de cambio USD→COP de 4.100 (editables en Configuración). |
| Catálogo | 7 tipos de producto, 6 categorías con 11 subcategorías y 15 productos en español e inglés con precios en COP y USD. |
| Inventario | 3 bodegas (General y Medellín en Colombia, Miami en Estados Unidos), 3 proveedores con sus productos oficiales, 3 órdenes recibidas (una parcial), 1 salida por daño y 2 ajustes. |
| Clientes y ventas | 5 clientes y 6 ventas: 3 confirmadas en COP, 1 en USD, 1 pendiente y 1 anulada. |

La carga es **idempotente**: busca antes de crear, y las operaciones solo se
cargan si los proveedores de la demostración aún no tienen órdenes. Empresas,
personas y correos son ficticios (dominio `.example`).

### 6.9 E11 — Despliegue en Render

Blueprint `render.yaml` con base PostgreSQL 16, backend en Docker y frontend
estático; la base se crea con `crear-bd` desde el equipo local. Paso a paso,
variables, respaldo y recuperación en la [sección 8](#8-despliegue-en-render-respaldo-y-restauración).

### 6.10 E12 — Cierre de la entrega

- **Checklist de la sección 5 de la guía**, verificado punto por punto
  ([7.6](#76-verificaciones-de-la-entrega)).
- **E2E también en USD** (antes solo COP): compra con tipo de cambio escrito en
  la interfaz, entrada en una bodega de Estados Unidos y venta con su impuesto.
- **`verificar-roles`**: nuevo comando que comprueba los accesos de cada rol.
- **Evidencias** de la corrida final en `docs/evidencias/`, revisadas sin
  secretos.
- **Este README** reorganizado para un lector nuevo.

---

## 7. Pruebas: qué se probó, cómo, con qué y dónde están los resultados

### 7.1 Resumen

| Suite | Herramienta | Qué prueba | Archivos | Pruebas |
|---|---|---|---|---:|
| `python` | **pytest** (+ ruff) | CLI, modelo, generador de SQL, semillas, base de datos real, respaldos, orquestador de pruebas y verificación de roles | `tools/tests/` | 67 |
| `backend` | **JUnit 5**, Spring Boot Test, MockMvc, **Testcontainers** | Reglas de negocio (unitarias) y la API completa contra PostgreSQL real | `backend/src/test/java/com/inventario/service/` | 74 |
| `frontend` | **Vitest** + Testing Library (+ oxlint y tsc) | Pantallas, cliente HTTP, permisos, formatos, decimales y traducciones | `frontend/src/**/*.test.ts(x)` | 37 |
| `e2e` | **Playwright** (Chromium) | Dos flujos completos en el navegador, en COP y en USD | `e2e/tests/flujo-venta.spec.ts` | 2 |
| **Total** | | | | **180, todas en verde** |

Corrida final (`python tools/cli.py todo`, 21-10-2026):
[`docs/evidencias/final/resumen.md`](docs/evidencias/final/resumen.md).

### 7.2 Cómo se ejecutan

Requisitos: opción B instalada ([4.5](#45-opción-b-desarrollo-local)) y
**Docker Desktop abierto**. Las pruebas **no usan tu base**: cada corrida crea un
PostgreSQL 16 desechable en Docker y lo elimina al terminar.

```bat
rem Las cuatro suites
python tools/cli.py probar
rem Solo algunas
python tools/cli.py probar --suites python,frontend
rem Qué se ejecutaría, sin ejecutar nada
python tools/cli.py probar --dry-run
rem La E2E sobre Docker Compose (como en el CI)
python tools/cli.py probar --suites e2e --e2e-entorno compose
rem La E2E contra un despliegue publicado (Render); ver 8.2, paso 7
python tools/cli.py probar --suites e2e --e2e-entorno remoto
rem validar + generar + crear-bd + probar
python tools/cli.py todo
```

| Suite | Pasos que ejecuta `probar` |
|---|---|
| `python` | `ruff check`, `ruff format --check` y `pytest`. |
| `backend` | `mvnw clean verify` (compila y ejecuta las pruebas con Testcontainers). |
| `frontend` | `npm ci`, `npm run lint` (oxlint), `npm run typecheck` (tsc), `npm test` (Vitest) y `npm run build`. |
| `e2e` | Instala Chromium y, según `--e2e-entorno`: **`local`** (por defecto) crea una base desechable con `crear-bd`, arranca el backend (JAR) y el frontend (`vite preview`) en puertos libres; **`compose`** levanta `docker-compose.yml` en un proyecto aislado con puertos y secretos propios y lo borra al final; **`remoto`** usa un despliegue ya publicado (`E2E_BASE_URL`, `E2E_API_URL`, `E2E_ADMIN_EMAIL`, `E2E_ADMIN_PASSWORD`). Después, Playwright. |

También se pueden ejecutar por separado, sin `probar`:

```bat
python -m pytest
cd backend && .\mvnw.cmd clean verify && cd ..
cd frontend && npm run lint && npm run typecheck && npm test && npm run build && cd ..
```

La E2E necesita una base, el backend y el frontend en marcha, por eso se ejecuta
siempre con `probar` (que los prepara) y no sola.

### 7.3 Detalle por suite

#### Python (pytest)

| Archivo | Pruebas | Qué verifica |
|---|---:|---|
| `test_model_validator.py` | 8 | El modelo JSON válido pasa; errores de tipos, nombres, claves y referencias se detectan. |
| `test_sql_generator.py` | 13 | SQL, metadatos del frontend y CRUD del backend generados desde el modelo; idempotencia de `generar`. |
| `test_database.py` | 17 | Conexión (`DB_*` y `DATABASE_URL`), protecciones de `reiniciar-bd`, migraciones con checksum, semillas y CLI. |
| `test_database_integracion.py` | 5 | **Base real en Docker:** `crear-bd` aplica V001–V003 y es idempotente, `reiniciar-bd` recrea, el saldo no puede ser negativo y los movimientos no se pueden modificar ni borrar. |
| `test_demo_seed.py` | 6 | Coherencia de `demo.json`, que no guarda contraseñas, y la regla para no duplicar operaciones. |
| `test_respaldo.py` | 7 | Retención de RNF-011, contraseña fuera de la línea de comandos, confirmación de `restaurar` y **respaldo y restauración reales** en un contenedor. |
| `test_test_runner.py` | 8 | Conteo de JUnit, `resumen.md` y opciones de `probar`. |
| `test_verificar_roles.py` | 3 | Informe de roles sin diferencias, detección de un acceso indebido y correo del administrador oculto. |

#### Backend (JUnit 5 · Spring Boot Test · MockMvc · Testcontainers)

Las clases `*PostgresIntegrationTest` heredan de `support/PostgresIntegrationTest`:
inician un **PostgreSQL 16 desechable** con Testcontainers, lo crean con
`python tools/cli.py crear-bd` (igual que en producción) y prueban la API con
MockMvc y los permisos reales. El resto son pruebas unitarias de servicios.

| Clase | Pruebas | Qué verifica |
|---|---:|---|
| `AutenticacionServiceTest` | 6 | Inicio de sesión, bloqueo por intentos y desbloqueo. |
| `JwtServiceTest` · `SesionTrackerTest` | 1 · 2 | Firma de tokens; sesiones e inactividad. |
| `ParametroServiceTest` · `ParametroAdminServiceTest` | 3 · 10 | Resolución de parámetros por ámbito y vigencia; administración y validaciones. |
| `ImpuestoServiceTest` · `ConversionMonedaServiceTest` | 3 · 4 | Cálculo de impuestos; conversión y redondeo de monedas. |
| `CatalogoServiceTest` · `CatalogoPostgresIntegrationTest` | 7 · 2 | Reglas de productos, atributos, traducciones y precios; API del catálogo. |
| `ClienteServiceTest` · `ClientePostgresIntegrationTest` | 4 · 1 | Reglas de clientes y consentimiento; API con alcance por país. |
| `InventarioEtapaServiceTest` · `InventarioPostgresIntegrationTest` | 6 · 2 | Reglas de inventario; flujo completo de compra, recepción, venta y ajuste con permisos, país y auditoría. |
| `VentasServiceTest` · `VentasListadoTest` | 4 · 5 | Cálculo y confirmación de ventas; listado y filtros. |
| `ReportesServiceTest` · `ReferenciasServiceTest` | 2 · 4 | Reportes; permisos de las referencias. |
| `ParametroVentasPostgresIntegrationTest` | 4 | Parámetros, países de venta y decimales como texto por HTTP. |
| `PaisMonedaPorConfiguracionPostgresIntegrationTest` | 1 | **País y moneda nuevos solo con datos**, con venta completa (E7). |
| `DatabaseSchemaVerifierTest` | 3 | Detección de diferencias entre la base y el modelo. |

#### Frontend (Vitest + Testing Library)

Las pruebas renderizan cada pantalla en un navegador simulado (jsdom) y
reemplazan la API con `src/test/mockApi.ts`, que también registra las
peticiones para comprobar qué se envió.

| Archivo | Pruebas | Qué verifica |
|---|---:|---|
| `api/apiClient.test.ts` | 2 | Envía el token y guarda el renovado; muestra el detalle localizado de los errores. |
| `components/LoginPage.test.tsx` | 1 | Envío de credenciales sin mostrar la contraseña. |
| `navigation.test.ts` | 2 | Menú según permisos; Inicio siempre visible sin dar acceso al negocio. |
| `components/CrudPage.test.tsx` | 4 | CRUD genérico: listado paginado, validación y envío del formulario, controles ocultos con solo lectura y campos de clientes. |
| `components/catalog/CatalogPage.test.tsx` | 3 | Traducciones (actualiza las existentes y crea las que faltan), errores al guardar visibles y precios como texto decimal. |
| `components/inventory/InventoryPage.test.tsx` | 3 | Aprobación de ajustes, pantalla del experto técnico sin referencias prohibidas y filtro de movimientos por tipo y fechas. |
| `components/purchases/PurchasesPage.test.tsx` | 5 | Orden en moneda base (tasa 1) y en USD (tasa obligatoria), envío, cancelación y vínculo de productos. |
| `components/sales/SalesPage.test.tsx` | 3 | Venta con desglose y pago, cambio de idioma sin recargar y país elegido aunque la lista llegue tarde. |
| `components/reports/ReportsPage.test.tsx` | 2 | Filtros aplicados al reporte y al CSV; filtros ocultos según permisos. |
| `components/settings/SettingsPage.test.tsx` | 3 | Parámetros con ámbito y vigencia, valor decimal enviado como texto y validación de JSON. |
| `utils/decimal.test.ts` · `utils/formatters.test.ts` | 4 · 3 | Decimales sin `float`; formatos por idioma. |
| `i18n/locales.test.ts` | 2 | `es.json` y `en.json` tienen las mismas claves y existen todas las usadas. |

#### Extremo a extremo (Playwright)

`e2e/tests/flujo-venta.spec.ts` abre Chromium y usa la aplicación como una
persona, en español:

1. **COP:** iniciar sesión → crear un producto en el catálogo → en *Compras*,
   vincularlo al proveedor, crear la orden de 5 unidades (tasa fija en 1) y
   enviarla → registrar la entrada → vender 2 unidades (357.000 COP con IVA del
   19 %) → confirmar el pago y ver el recibo → comprobar existencia 3 y el
   movimiento "Salida por venta".
2. **USD:** orden a un proveedor en USD **escribiendo el tipo de cambio** →
   entrada en una bodega de Estados Unidos → venta de 1 unidad a 100 USD (106
   USD con el impuesto del 6 %) → pago → existencia 2.

Solo el tipo de producto, las categorías, el proveedor y la bodega se preparan
por la API (son CRUD genéricos ya probados), para que la prueba sea corta.

### 7.4 Dónde quedan los resultados

| Origen | Ubicación |
|---|---|
| `python tools/cli.py probar` | `logs/tests/<AAAAMMDD-HHMMSS>/`: `resumen.md` (estado, pruebas, fallos y duración por suite, con el commit evaluado), `<suite>.log` (salida completa de cada paso) y `<suite>/` (reportes JUnit XML). |
| Capturas y traza de la E2E si falla | `logs/tests/<fecha-hora>/e2e/artefactos/` (abrir con `npx playwright show-trace <trace.zip>` desde `e2e/`). |
| Comandos del CLI | `logs/tools/<comando>-<fecha>.log` |
| `mvnw verify` ejecutado a mano | `backend/target/surefire-reports/` |
| Playwright ejecutado a mano | `e2e/reports/` |
| CI de GitHub | Artefactos `logs-python`, `logs-backend`, `logs-frontend` y `logs-e2e` de cada ejecución (pestaña **Actions** → ejecución → **Artifacts**), y el `resumen.md` en la página de la ejecución. |
| **Evidencias de la entrega** (versionadas) | [`docs/evidencias/`](docs/evidencias/README.md): `final/` (corrida final de `todo`), `render/` (E2E contra Render) y `roles-y-permisos.md`. |

`logs/` y `backups/` no se versionan (están en `.gitignore`); por eso las
evidencias de la entrega se copiaron a `docs/evidencias/`. Ejemplo de
`resumen.md` de la corrida final:

```text
| Suite | Estado | Pruebas | Fallos | Errores | Omitidas | Duración | Log |
| python | ✅ correcto | 67 | 0 | 0 | 0 | 18 s | python.log |
| backend | ✅ correcto | 74 | 0 | 0 | 0 | 30 s | backend.log |
| frontend | ✅ correcto | 37 | 0 | 0 | 0 | 73 s | frontend.log |
| e2e | ✅ correcto | 2 | 0 | 0 | 0 | 44 s | e2e.log |
```

### 7.5 Si una prueba falla

- **"Docker no está disponible"** o pruebas de integración omitidas: abre Docker
  Desktop.
- **Testcontainers no encuentra Docker (error 400):** ajusta `api.version` en
  `backend/src/test/resources/docker-java.properties` (hoy `1.44`, la que exige
  Docker Engine 29).
- Repite solo esa suite (`--suites <nombre>`) y revisa su `<suite>.log`; en la
  E2E, la captura y la traza de `e2e/artefactos/`.

### 7.6 Verificaciones de la entrega

Checklist de la sección 5 de [`spec/GUIDE.md`](spec/GUIDE.md):

| Punto | Estado | Cómo se comprobó | Evidencia |
|---|---|---|---|
| `python tools/cli.py todo` termina sin errores | ✅ | `todo` completo: modelo válido, `generar` sin diferencias, base al día y 180 pruebas en verde. | [`final/todo.txt`](docs/evidencias/final/todo.txt) |
| `docker compose up --build` deja el sistema funcionando | ✅ | E2E sobre Docker Compose (local y en el CI) y una instalación desde `git clone` en una carpeta nueva. | Trabajo `e2e` del CI |
| Un usuario de cada rol con los permisos correctos | ✅ | `python tools/cli.py verificar-roles`: 5 roles, 8 consultas y 8 permisos, todo coincide con el Anexo D. | [`roles-y-permisos.md`](docs/evidencias/roles-y-permisos.md) |
| Producto → entrada → venta (COP y USD) → saldo y movimiento | ✅ | Las dos pruebas E2E. | [`final/e2e.log`](docs/evidencias/final/e2e.log) |
| Cambio de idioma y de moneda sin recargar | ✅ | `SalesPage.test.tsx` y `locales.test.ts`; selector de idioma y de moneda en Inicio. | [`final/frontend.log`](docs/evidencias/final/frontend.log) |
| País y moneda nuevos solo con datos | ✅ | `PaisMonedaPorConfiguracionPostgresIntegrationTest`. | [`final/backend/`](docs/evidencias/final/backend/) |
| CI en verde y artefactos descargables | ✅ | 4 trabajos obligatorios en `main`, con artefactos `logs-*`. | Pestaña **Actions** |
| `docs/evidencias/` con los logs de la corrida final | ✅ | Copiados y revisados sin secretos. | [`docs/evidencias/`](docs/evidencias/README.md) |
| URL públicas funcionando | ✅ | `probar --e2e-entorno remoto` contra Render. | [`render/resumen.md`](docs/evidencias/render/resumen.md) |
| README completo y sin secretos en el repositorio | ✅ | Este documento; `detect-secrets` en cada commit; `.env` y `backups/` ignorados. | `.secrets.baseline` |

```bat
rem Repetir la verificación de roles contra Docker y guardar el informe
python tools/cli.py verificar-roles --api http://localhost:8080 --salida docs\evidencias\roles-y-permisos.md
```

---

## 8. Despliegue en Render, respaldo y restauración

### 8.1 Qué se despliega

[Render](https://render.com) crea los tres recursos de `render.yaml` (un
*Blueprint*):

| Recurso | Nombre | Detalle | URL actual |
|---|---|---|---|
| Base de datos | `pixelvault-db` | PostgreSQL 16 gratuito, región `virginia`. | (privada) |
| Backend | `pixelvault-api` | La misma imagen de `docker/backend.Dockerfile`, salud en `/actuator/health`. | https://pixelvault-api-ljq6.onrender.com |
| Frontend | `pixelvault-web` | Sitio estático: `npm ci && npm run build` en `frontend/` con Node 22; reescribe cualquier ruta a `index.html`. | https://pixelvault-web-ceen.onrender.com |

Render agregó sufijos (`-ljq6`, `-ceen`) porque los nombres ya estaban ocupados.
Cada servicio se vuelve a desplegar solo cuando cambian sus archivos en `main`
**y el CI pasó** (`autoDeployTrigger: checksPass`). Si un servicio no se
actualiza tras fusionar un PR: en su panel, *Settings* → *Auto-Deploy* en
**After CI Checks Pass**, y luego *Manual Deploy* → *Deploy latest commit*.

**Condiciones del plan gratuito** (verificadas el 21-10-2026 en
https://render.com/docs/free): el backend se suspende tras **15 minutos sin
tráfico** y tarda **alrededor de 1 minuto** en despertar; la base **caduca a los
30 días** (14 días de gracia y luego se borra); 1 GB de base; 750 horas de
servicio al mes.

| Variable | Servicio | Cómo se define |
|---|---|---|
| `SPRING_DATASOURCE_URL` | backend | A mano: forma **JDBC** del host **interno** de la base, por ejemplo `jdbc:postgresql://dpg-xxxx-a:5432/inventario_xxxx`. |
| `SPRING_DATASOURCE_USERNAME` · `SPRING_DATASOURCE_PASSWORD` | backend | Automático desde la base (`fromDatabase`). |
| `JWT_SECRET` | backend | Automático: Render genera 256 bits en base64 (`generateValue`). |
| `CORS_ORIGINS` | backend | A mano: URL del frontend. |
| `SPRING_PROFILES_ACTIVE`, `PORT`, `SERVER_PORT` | backend | Fijos: `produccion`, `8080`, `8080`. |
| `VITE_API_URL` | frontend | A mano: URL del backend (si cambia, hay que volver a desplegar el sitio). |
| `NODE_VERSION` | frontend | Fijo: `22`. |

### 8.2 Paso a paso para desplegar en una cuenta nueva

1. **Crear el Blueprint:** en https://dashboard.render.com → **New** →
   **Blueprint** → elegir el repositorio y la rama `main`. Render pide tres
   valores; si aún no conoces las URL, escribe
   `https://pixelvault-web.onrender.com` (`CORS_ORIGINS`),
   `https://pixelvault-api.onrender.com` (`VITE_API_URL`) y
   `jdbc:postgresql://pendiente:5432/inventario` (`SPRING_DATASOURCE_URL`).
   Pulsa **Apply**. El primer despliegue del backend fallará: es normal, la base
   aún está vacía.
2. **Copiar las URL de la base:** en **pixelvault-db** → **Connections**:
   *Internal Database URL* (para el backend) y *External Database URL* (para tu
   equipo). Son secretas: guárdalas fuera del proyecto.
3. **Crear la base desde tu equipo** (`cmd`, con el entorno de Python activado;
   los `set` duran mientras la ventana esté abierta):

   ```bat
   .venv\Scripts\activate.bat
   set "DATABASE_URL=<External Database URL>?sslmode=require"
   set "ADMIN_EMAIL=<correo del administrador de Render>"
   set "ADMIN_PASSWORD=<contraseña distinta de la local, sin % ni comillas>"
   python tools/cli.py crear-bd
   ```

4. **Conectar el backend:** en **pixelvault-api** → **Environment**, cambia
   `SPRING_DATASOURCE_URL` por la forma JDBC de la URL **interna**:
   `postgresql://usuario:clave@dpg-xxxx-a/inventario_xxxx` →
   `jdbc:postgresql://dpg-xxxx-a:5432/inventario_xxxx` (antepón `jdbc:`, quita
   `usuario:clave@` y agrega `:5432`). **Save, rebuild and deploy**.
5. **Revisar las URL reales:** si Render agregó sufijos, corrige `CORS_ORIGINS`
   (backend) y `VITE_API_URL` (frontend) y vuelve a desplegar ambos.
6. **Verificar:**

   ```bat
   curl https://pixelvault-api-ljq6.onrender.com/actuator/health
   ```

   Debe responder `{"status":"UP"}`. Luego entra a la URL del frontend con el
   administrador del paso 3.
7. **Repetir la E2E en la URL pública** (deja datos de prueba con "E2E" en el
   nombre):

   ```bat
   set "E2E_BASE_URL=https://pixelvault-web-ceen.onrender.com"
   set "E2E_API_URL=https://pixelvault-api-ljq6.onrender.com"
   set "E2E_ADMIN_EMAIL=<correo del paso 3>"
   set "E2E_ADMIN_PASSWORD=<contraseña del paso 3>"
   python tools/cli.py probar --suites e2e --e2e-entorno remoto
   ```

8. **(Opcional) Datos de demostración**, en la misma ventana:

   ```bat
   set "DEMO_PASSWORD=<contraseña común de los usuarios demo>"
   python tools/cli.py sembrar-demo --api https://pixelvault-api-ljq6.onrender.com
   ```

**Antes de cada demostración:** abrir la aplicación unos 2 minutos antes y
revisar en el panel la fecha de caducidad de la base.

### 8.3 Respaldo y restauración

Cumplen RNF-011 en la versión académica. `pg_dump` y `pg_restore` se ejecutan
**dentro de la imagen Docker `postgres:16-alpine`** (no hace falta instalar
PostgreSQL y la versión coincide con la de la base); la contraseña viaja como
variable de entorno, nunca en la línea de comandos. Código: `tools/respaldo.py`.

```bat
rem Respaldar la base de .env (o la de DATABASE_URL si está definida)
python tools/cli.py respaldar
rem Respaldar y aplicar la retención: todas las copias de 35 días y una mensual durante 12 meses
python tools/cli.py respaldar --podar
rem Restaurar: reemplaza TODO el contenido de la base, exige confirmar su nombre exacto
python tools/cli.py restaurar --archivo backups\inventario-20261021-020000.dump --confirm-db inventario
```

- Los respaldos quedan en `backups/<base>-<AAAAMMDD-HHMMSS>.dump` (hora UTC),
  carpeta ignorada por Git porque contiene datos reales.
- `restaurar` usa una sola transacción (si falla, la base queda como estaba) y
  al final verifica el esquema.
- **Respaldo diario automático** con el Programador de tareas de Windows: crear
  fuera del repositorio `C:\respaldos-pixelvault\respaldar-render.cmd` con:

  ```bat
  @echo off
  cd /d C:\proyectos\Proyecto_Inventario_Videojuegos
  set "DATABASE_URL=<External Database URL>?sslmode=require"
  .venv\Scripts\python.exe tools\cli.py respaldar --podar
  ```

  y programarlo: `schtasks /create /tn "PixelVault respaldo" /sc daily /st 02:00 /tr "C:\respaldos-pixelvault\respaldar-render.cmd"`
  (el equipo debe estar encendido y con Docker Desktop abierto).

**Si la base de Render caduca:** crear una nueva (*New* → *Postgres*, versión
16, región `virginia`), restaurar el último respaldo con su URL externa en
`DATABASE_URL` (o, sin respaldo, `crear-bd`), y actualizar
`SPRING_DATASOURCE_URL` (y usuario y contraseña si la base no quedó enlazada al
Blueprint). Toma minutos, muy por debajo de las 3 horas de RNF-011.

---

## 9. Integración continua y protección de `main`

### 9.1 GitHub Actions

`.github/workflows/ci.yml` se ejecuta en cada pull request y en cada fusión a
`main`. Son cuatro trabajos en paralelo; cada uno ejecuta el mismo comando que
en local (`python tools/cli.py probar --suites <suite>`) y sube sus logs como
artefacto aunque falle:

| Trabajo | Qué comprueba | Artefacto |
|---|---|---|
| Python (ruff, pytest, validar y generado) | `validar`; que `generar` no cambie nada de lo versionado; ruff y pytest con base desechable. | `logs-python` |
| Backend (mvn verify con Testcontainers) | Compilación y pruebas del backend. | `logs-backend` |
| Frontend (lint, typecheck, test y build) | oxlint, tsc, Vitest y compilación. | `logs-frontend` |
| Extremo a extremo (Playwright) | Construye las imágenes de Docker y ejecuta la E2E sobre Docker Compose. | `logs-e2e` |

El CI corre en servidores Linux de GitHub porque las pruebas usan contenedores
Linux (PostgreSQL y las imágenes del proyecto); **en tu equipo no necesitas
Linux**: Docker Desktop los ejecuta en Windows.

### 9.2 Revisiones locales antes de cada commit y push

`pre-commit install` activa:

- **Antes de cada commit:** ruff, formato y `detect-secrets` (bloquea
  contraseñas o claves).
- **Antes de cada push:** `validar`, `pytest` y las pruebas de Vitest.

```bat
pre-commit run --all-files
pre-commit run --hook-stage pre-push --all-files
```

### 9.3 Protección de `main`

Ruleset de GitHub **"Proteger main - CI obligatorio."** (*Settings → Rules →
Rulesets*): no se puede hacer `push` directo a `main`; todo cambio llega por
pull request y solo se puede fusionar con los **cuatro trabajos del CI en
verde**; `main` no se puede borrar ni reescribir (*force push*).

Flujo de trabajo para un cambio:

```bat
git switch main
git pull
git switch -c mi-cambio
rem ...editar y probar: python tools/cli.py probar
git add -A
git commit -m "descripción del cambio"
git push -u origin mi-cambio
rem En GitHub: abrir el pull request hacia main, esperar los 4 checks en verde y fusionar.
git switch main
git pull
git branch -d mi-cambio
```

---

## 10. Problemas encontrados y cómo se corrigieron

| Etapa | Problema | Causa | Corrección |
|---|---|---|---|
| E6.3/E6.4 | El backend no compilaba. | `ParametroAdminRepository` llamaba a un método inexistente. | `RowMapper` propio. |
| E6.3/E6.4 | El listado de parámetros fallaba. | Consulta `COUNT` sin `FROM`. | Consulta corregida y probada. |
| E6.3/E6.4 | Un usuario limitado por país podía crear parámetros de otro país. | Faltaba validar el país destino. | Validación al crear y actualizar. |
| E6.3/E6.4 | Decimales de parámetros perdían precisión. | Llegaban como `double`. | Texto decimal. |
| E6.3/E6.4 | La exportación CSV se cortaba. | Spring Security rechazaba el despacho asíncrono. | Se permiten `ASYNC` y `ERROR` de una petición ya autorizada. |
| E6.3/E6.4 | El backend no arrancaba sobre una base nueva. | `bodega.pais_id` obligatorio en el modelo, nulo en las migraciones. | Migración V003 y país en la semilla. |
| E6.3/E6.4 | No se podían aprobar ajustes. | Estado mal escrito en el frontend (`PENDIENTE_APROBACION`). | Estados tomados del modelo. |
| E6.3/E6.4 | Inventario fallaba para experto técnico y vendedor. | Se pedían proveedores sin permiso. | Referencias opcionales y pestañas según permisos. |
| E6.3/E6.4 | Cambios de nombre de productos no se guardaban. | Errores ignorados al reenviar traducciones. | `PUT`/`POST` correctos y errores visibles. |
| E6.3/E6.4 | Productos visibles solo como `PRD-<UUID>`. | Contrato de referencias equivocado. | Referencia `producto` con nombre por idioma. |
| E6.3/E6.4 | 13 advertencias de lint y textos fuera de i18n. | Efectos y textos fijos. | Hook `useApiQuery`; todo en `es.json`/`en.json`. |
| E10 | Resumen falso "e2e ❌" en GitHub. | Una prueba unitaria escribía en `GITHUB_STEP_SUMMARY`. | La prueba borra esa variable. |
| E10 | Instrucciones en PowerShell y carga manual del `.env`. | — | Comandos en `cmd`; el backend lee `.env` solo. |
| E10 | La pila de Docker arrancaba sola. | `restart: unless-stopped`. | Sin reinicio automático. |
| E10 | Definición de atributo con `"opciones": null` rechazada. | Jackson lo recibe como nodo nulo. | `null` equivale a sin opciones. |
| E10 | Archivos sin uso (copias de la especificación, restos de Vite, `.gitkeep`). | — | Eliminados. |
| E11 | E2E en Render se detenía al vender ("Bodega" desactivada). | El país se elegía solo si la lista ya había llegado; Render es más lento. | El formulario elige el país aunque la lista llegue tarde; prueba de regresión. |
| E11 | El CI falló en el backend a las 00:59 UTC. | La prueba guardaba el precio con la fecha del equipo y la venta usa la de Colombia. | La prueba usa la zona horaria del país (verificada en UTC, UTC+14 y local). |
| E11 | En Render, `sembrar-demo` no cargó compras ni ventas. | Se omitían si existía cualquier orden (la de la E2E). | Solo cuentan las órdenes de los proveedores de la demo. |
| E11 | Render no se actualizaba tras fusionar. | Despliegue automático no activo. | `autoDeployTrigger: checksPass` en `render.yaml` y ajuste en el panel. |
| E11 | La tarjeta "Cantidad 1.234.567,89" del Inicio parecía un dato real. | Etiqueta ambigua de un ejemplo de formato. | "Número de ejemplo" y descripción aclaratoria. |
| E12 | Clonar en una carpeta profunda fallaba (`Filename too long`). | Evidencias con rutas de 127 caracteres + límite de 260 de Windows. | Rutas de evidencias acortadas (máximo del repositorio: 99) e instrucciones de clonado en ruta corta. |

---

## 11. Ramas, pull requests e historial

**Hoy el repositorio solo tiene la rama `main`.** Todas las ramas secundarias
se fusionaron mediante pull request y se borraron: no queda ninguna rama
necesaria fuera de `main`. Las etapas E5 a E9 se integraron con commits previos
a la protección de `main` (E9); desde entonces todo entró por pull request:

| PR | Rama | Contenido |
|---|---|---|
| #1 | `docs/proteccion-main` | Documentación de la protección de `main` (E9). |
| #2 | `prueba/ci-rojo-bloquea` | Cambio con una prueba rota a propósito para comprobar que el CI bloquea la fusión; se cerró sin fusionar. |
| #3 | `e10-contenedores` | Imágenes de Docker y Docker Compose (E10). |
| #4 | `correcciones-e10` | Revisión general, limpieza y soporte de `cmd`. |
| #5 | `datos-demo-y-estilo` | Datos de demostración e identidad visual. |
| #6 | `compras-ui` | Pantalla de Compras. |
| #7 | `e11-render` | Despliegue en Render, respaldo y restauración (E11). |
| #8 | `fix-venta-pais-lento` | País de la venta con servidor lento y prueba sensible a la hora. |
| #9 | `demo-operaciones-por-proveedor` | Carga demo completa en Render y etiqueta del Inicio. |
| #10 | `e12-cierre` | Cierre: evidencias, E2E en USD y `verificar-roles` (E12). |
| #11 | `e12-readme-final` | Este README final, rutas cortas de evidencias y `.env.example` aclarado. |

```bat
rem Ver el historial de fusiones
git log --merges --oneline
rem Ver las ramas que existen (local y remoto)
git branch -a
```

---

## 12. Estructura del proyecto

```text
Proyecto_Inventario_Videojuegos/
├── README.md                     # Este documento
├── .env.example                  # Plantilla de configuración (sin secretos); copiar a .env
├── .gitignore                    # Excluye .env, logs/, backups/, dependencias y compilaciones
├── .gitattributes                # Saltos de línea LF en los archivos que usan los contenedores
├── .dockerignore                 # Contexto mínimo para construir las imágenes
├── .pre-commit-config.yaml       # Revisiones de commit (ruff, secretos) y push (validar, pruebas)
├── .secrets.baseline             # Falsos positivos aceptados por detect-secrets
├── pyproject.toml                # Configuración de ruff y pytest
├── requirements.txt              # Dependencias de Python para ejecutar tools/ (versión fija)
├── requirements-dev.txt          # requirements.txt + pytest, ruff, pre-commit y detect-secrets
├── docker-compose.yml            # Servicios db, db-init, backend y frontend (E10)
├── render.yaml                   # Blueprint de Render: base, backend y frontend (E11)
├── .github/workflows/ci.yml      # CI de GitHub Actions: 4 trabajos (E9)
├── spec/                         # Especificación (no se modifica al programar)
│   ├── REQUIREMENTS.md           # Requisitos funcionales y no funcionales
│   ├── GUIDE.md                  # Guía por etapas, checklist de entrega y anexos
│   ├── INSTRUCTIONS.md           # Reglas de trabajo por etapas
│   └── modelo-conceptual.md      # E1: entidades, reglas, estados y permisos
├── db/
│   ├── model/
│   │   ├── model.schema.json     # JSON Schema del modelo (E2)
│   │   ├── modulos/              # catalogo, inventario, parametrizacion, seguridad,
│   │   │                         # terceros, transversal y ventas (.json)
│   │   └── schema-resuelto.json  # Modelo resuelto (generado; lo valida el backend)
│   ├── migrations/               # V001 (generada), V002 y V003
│   └── seeds/
│       ├── inicial.json          # Datos iniciales (crear-bd)
│       └── demo.json             # Datos de demostración (sembrar-demo)
├── tools/                        # CLI de Python (python tools/cli.py ...)
│   ├── cli.py                    # Punto de entrada de todos los comandos
│   ├── model_validator.py        # validar
│   ├── sql_generator.py          # generar (SQL, CRUD del backend, metadatos del front)
│   ├── templates/migration.sql.j2
│   ├── database.py               # Conexión, migraciones con checksum y verificación
│   ├── seed_loader.py            # Semillas idempotentes
│   ├── demo_seed.py              # sembrar-demo (vía API)
│   ├── respaldo.py               # respaldar / restaurar (pg_dump en Docker)
│   ├── verificar_roles.py        # verificar-roles (Anexo D)
│   ├── test_runner.py            # probar: suites, logs y resumen.md
│   ├── postgres_desechable.py    # PostgreSQL temporal en Docker para pruebas
│   ├── env_config.py             # Lectura de .env
│   └── tests/                    # 8 archivos de pytest (67 pruebas)
├── backend/                      # Spring Boot (Java 17)
│   ├── pom.xml, mvnw, mvnw.cmd, .mvn/
│   └── src/
│       ├── main/java/com/inventario/
│       │   ├── InventarioApplication.java
│       │   ├── config/           # CORS, idioma, JSON (decimales como texto), OpenAPI, esquema
│       │   ├── security/         # Filtro JWT, cadena de seguridad, errores 401/403
│       │   ├── controller/       # Endpoints REST /api/v1
│       │   ├── service/          # Reglas de negocio, transacciones y auditoría
│       │   ├── repository/       # SQL con JDBC
│       │   ├── domain/, dto/     # Datos internos y contratos de la API
│       │   └── generated/        # SQL de CRUD generado (no editar a mano)
│       ├── main/resources/       # application.yml, perfiles local/produccion, mensajes es/en
│       ├── test/java/com/inventario/service/  # 20 clases de prueba (74 pruebas)
│       ├── test/java/com/inventario/support/  # Base de Testcontainers + crear-bd
│       └── test/resources/       # docker-java.properties
├── frontend/                     # React + TypeScript + Material UI (Vite)
│   ├── package.json, vite.config.ts, tsconfig*.json, .oxlintrc.json, index.html
│   ├── public/favicon.svg        # Logo de PixelVault
│   └── src/
│       ├── main.tsx, App.tsx, theme.ts, navigation.ts, App.css, index.css
│       ├── api/                  # Cliente HTTP, useApiQuery, errores y tipos
│       ├── auth/                 # Sesión, identidad y renovación del token
│       ├── crud/metadata.ts      # Metadatos del CRUD genérico y permisos
│       ├── generated/            # entidades.json / entidades.ts (generados)
│       ├── i18n/                 # Configuración y locales es.json / en.json
│       ├── components/
│       │   ├── AppShell, Dashboard, LoginPage, LanguageSwitcher, CrudPage,
│       │   │   PageHeader, CrudEntityLinks, PixelVaultLogo
│       │   ├── catalog/          # Catálogo de productos
│       │   ├── inventory/        # Existencias, entradas, salidas, ajustes y movimientos
│       │   ├── purchases/        # Órdenes de compra y productos por proveedor
│       │   ├── sales/            # Ventas, detalle y recibo
│       │   ├── reports/          # Reportes y CSV
│       │   └── settings/         # Parámetros y configuración
│       ├── utils/                # Formatos Intl, decimales y referencias
│       └── test/                 # Simulador de API (mockApi.ts) y configuración de Vitest
│                                 # (13 archivos *.test.ts(x), 37 pruebas, junto a cada módulo)
├── e2e/                          # Playwright
│   ├── package.json, playwright.config.ts
│   └── tests/flujo-venta.spec.ts # Flujos completos en COP y en USD
├── docker/
│   ├── backend.Dockerfile        # JDK 17 (compila) → JRE 21 (ejecuta)
│   ├── frontend.Dockerfile       # Node 22 (compila) → nginx (sirve)
│   ├── nginx.conf                # Sitio estático con respaldo a index.html
│   └── db-init.Dockerfile        # Python 3.12 que ejecuta crear-bd
├── docs/evidencias/              # Evidencias de la entrega (versionadas)
│   ├── README.md                 # Índice de las evidencias
│   ├── final/                    # Corrida final de todo: resumen.md, logs, JUnit, todo.txt
│   ├── render/                   # E2E contra las URL públicas de Render
│   └── roles-y-permisos.md       # Informe de verificar-roles
├── logs/                         # (No versionado) tools/ y tests/<fecha-hora>/
└── backups/                      # (No versionado) respaldos .dump
```

---

## 13. Decisiones técnicas

| Decisión | Por qué |
|---|---|
| Modelo de datos en JSON y generación de SQL, CRUD y metadatos con Python | Una sola fuente de verdad legible y corregible; el resto se regenera. |
| La base la crea solo `crear-bd`; migraciones con checksum, sin Flyway; backend con `ddl-auto=validate` | Lo exige la guía; el backend nunca altera el esquema. |
| Spring Boot con JDBC y SQL generado, sin entidades JPA | El SQL se ve y se corrige; JPA solo valida el esquema. |
| Importes y tasas como texto decimal en toda la API | Sin errores de redondeo de `float` entre Java, JSON y JavaScript. |
| Nada de negocio en el código (países, monedas, impuestos, medios de pago y límites son datos con ámbito y vigencia) | Agregar un país o cambiar un impuesto no requiere programar. |
| JWT con registro de sesiones, inactividad y bloqueo configurables | Seguridad acorde con RNF-003. |
| React + Material UI con i18next e `Intl` | Cambio de idioma y de formato al instante. |
| Cuatro suites con un solo comando y base desechable | Se repiten igual en cualquier equipo y en el CI, sin tocar datos reales. |
| CI en GitHub Actions y `main` protegida | Nada entra a `main` con pruebas en rojo. |
| Docker Compose en local, Render para la demostración y respaldos con `pg_dump` en Docker | Mismo backend en ambos sitios; la base gratuita se recupera en minutos. |

## 14. Cómo corregir a mano

| Quiero cambiar… | Dónde | Después |
|---|---|---|
| Países, monedas, idiomas, impuestos, tipos de cambio o parámetros | La aplicación: **Configuración** (como administrador). | Nada más: son datos, con auditoría. |
| Datos iniciales de una base nueva | `db/seeds/inicial.json` | `python tools/cli.py crear-bd` (solo inserta lo que falta). |
| Datos de demostración | `db/seeds/demo.json` | `python tools/cli.py sembrar-demo` sobre una base limpia. |
| Una tabla o columna | `db/model/modulos/*.json` | `python tools/cli.py validar` y `generar`; escribir `db/migrations/V004__<descripción>.sql` con el cambio; `python tools/cli.py crear-bd`; versionar todo junto. `V001` no se toca. |
| SQL de CRUD o metadatos del frontend | No a mano: los produce `generar`. | El CI comprueba que lo generado coincide con el modelo: corrige el JSON o `tools/templates/`. |
| Un texto de la interfaz | `frontend/src/i18n/locales/es.json` y `en.json` | `cd frontend && npm test` comprueba que no falten claves. |
| Un mensaje de error del backend | `backend/src/main/resources/messages.properties` y `messages_en.properties` | Reiniciar el backend. |
| Una variable de entorno | `.env` (local y Docker) o el panel de Render | Reiniciar el servicio (en Render, `VITE_API_URL` exige volver a desplegar el sitio). |

Si algo falla: logs de los comandos en `logs/tools/` y de las pruebas en
`logs/tests/<fecha-hora>/`.

## 15. Fase 2 y pendientes conocidos

Fuera de la primera entrega según la sección 7.3 de los requisitos (solo
documentado, no construido):

| Requisito | Tema |
|---|---|
| RF-011 | Devoluciones (de clientes, con inspección, y a proveedores) |
| RF-012 | Números de serie por unidad |
| RF-013 | Garantías y servicio técnico |
| RF-014 | Canal de comercio electrónico para clientes |
| RF-016 | Recompra de productos usados |
| RF-017 | Traslados entre bodegas |
| RF-018 | Alertas de stock mínimo y máximo |
| RF-019 | Preventas y reservas |
| RF-024 | Envíos y logística de entrega |
| RF-025 | Comercio exterior en ventas: aranceles, impuestos de importación y aduanas |
| RF-026 | Importaciones en compras a proveedores de otros países |
| — | Autenticación multifactor, facturación electrónica real y pasarela de pagos real |

Pendientes detectados dentro del alcance actual:

- **Administración de usuarios:** no hay pantalla ni API para crear usuarios ni
  cambiar contraseñas (el administrador se crea con `crear-bd`, los usuarios
  demo con `sembrar-demo` y solo existe el desbloqueo).
- **Clientes:** reclasificación automática (frecuente/infrecuente) y retención o
  eliminación a 12 meses sin implementar.
- **CSV:** nombres de archivo fijos (CORS no expone `Content-Disposition`).
- **Frontend:** el paquete supera 500 kB (aviso de Vite); se puede dividir por
  pantallas.
- **Render gratuito:** la base caduca a los 30 días y el backend se suspende sin
  tráfico (ver [8.3](#83-respaldo-y-restauración)).
- **CI:** GitHub avisa que `actions/checkout@v4`, `setup-java@v4`,
  `setup-python@v5` y `upload-artifact@v4` usan Node.js 20 (en desuso) y que
  `ubuntu-latest` pasa a Ubuntu 26; hoy son solo avisos.
