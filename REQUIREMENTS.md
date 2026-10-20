# Especificación de requisitos — Sistema de inventario para tienda de consolas y videojuegos

**Versión del documento:** 0.7

## 1. Alcance y criterio de documentación

Este documento constituye el **levantamiento de requisitos** para un sistema de inventario de una tienda de consolas y videojuegos. Se utiliza como referencia:

- **ISO/IEC/IEEE 29148:2018**, para estructurar, documentar, verificar y gestionar requisitos de sistemas y software. La norma aborda procesos de ingeniería de requisitos, los elementos de información que deben producirse y sus contenidos.
- **ISO/IEC 25010:2023**, para identificar atributos de calidad del producto software y convertirlos en requisitos no funcionales medibles.

> **Importante:** la mayoría de las políticas comerciales y de operación (medios de pago, impuestos, garantías, sucursales, proveedores, ventas, devoluciones, seguridad de acceso, auditoría, reportes, hospedaje y base de datos) fueron confirmadas por el cliente y se incorporaron a los requisitos correspondientes. Los estados usados en este documento son **Confirmado** (lo definió el cliente) y **Decidido** (lo definió el equipo del proyecto para cerrar el levantamiento; es parametrizable, puede revisarse y está listado en la sección 7.1). No queda nada abierto para el alcance académico; la sección 7.2 recoge consideraciones para una operación real y la sección 7.3 define qué se construye en la primera entrega.

### Principios transversales

1. **Totalmente parametrizable:** ninguna regla, valor, texto, catálogo, formato ni integración propia de un negocio o de un país está fijada en el código. Todo se configura por el administrador, con valor por defecto, ámbito y vigencia, y cada cambio queda auditado. Los valores citados en este documento (plazos, porcentajes, umbrales, tarifas) son **valores por defecto configurables**, no valores fijos.
2. **Internacional:** una sola tienda vende en línea a clientes de varios países y compra a proveedores de varios países; el sistema no solo se traduce a otros idiomas. Cada país aporta su propia configuración: moneda (con su denominación, símbolo y formato), idioma, zona horaria, impuestos, aranceles y reglas aduaneras, envíos, documentos fiscales, medios de pago, formatos de datos de personas, normas de protección de datos y plazos legales.
3. **Comercio exterior incluido:** las ventas y compras en línea entre países siguen siendo comercio exterior. El envío, el paso por aduana, los aranceles y los impuestos de importación forman parte del sistema y no se dejan fuera por ser operaciones en línea.
4. **Colombia como país de origen y primer país de operación:** los ejemplos de este documento (DIAN, Nequi, Daviplata, Ley 1581 de 2012) son la configuración del primer país. No son una restricción del sistema: Colombia se modela como un país configurado más.

## 2. Actores y partes interesadas identificadas

### Actores potenciales del sistema

| Actor | Interacción esperada | Confirmación / decisión |
|---|---|---|
| Administrador | Gestionar usuarios, productos, inventario, configuraciones y parametrización, y consultar información general; aprueba todo ajuste de inventario y participa en los ajustes que afectan al negocio; consulta (sin modificar) los registros de auditoría. | Permisos definidos en la matriz de roles de RF-006. |
| Encargado de inventario | Registrar, consultar y ajustar existencias; recibir mercancía y realizar conteos; realizar traslados entre bodegas; preparar y despachar envíos; consultar la auditoría de inventario. | Decidido: es un rol distinto del Experto técnico (ver matriz en RF-006). |
| Experto técnico | Ejecutar ajustes de nivel técnico, diagnósticos, reparaciones e historial de servicio por número de serie; revisar devoluciones y productos de recompra; consultar la auditoría de servicio técnico. | Decidido: es un rol distinto del Encargado de inventario (ver matriz en RF-006). |
| Vendedor/cajero | Consultar productos y registrar ventas, ya que el sistema incluye el proceso de venta. | Permisos definidos en la matriz de roles de RF-006. |
| Gerente/propietario | Consultar reportes, inventario y movimientos para apoyar decisiones del negocio; todos los reportes definidos son obligatorios. | Los reportes admiten filtros por periodo, país, moneda, producto y usuario (RF-008). |
| Proveedor | Proveedor oficial por marca/producto (p. ej. Sony PlayStation para productos PlayStation), nacional o extranjero; interviene en órdenes de compra, comparación de costos, devoluciones y reportes de pérdida/daño/robo/defecto. | Confirmado: solo se registra como dato (sin acceso al sistema ni integración). Proveedores oficiales iniciales: sección 7.1, punto 11. |
| Cliente | Cliente de cualquier país habilitado. Se registra como cliente frecuente (datos completos: nombre, apellidos, código postal, dirección, teléfono, correo) si compra más de dos veces, o como infrecuente (retención reducida) si compra una o dos veces. | Decidido: los datos de contacto del cliente infrecuente se conservan 12 meses desde su última compra (parametrizable). Los datos de las facturas emitidas se conservan según la norma fiscal. |
| Sistema de pagos | Sistema externo integrado vía API para procesar los medios de pago de cada país: efectivo, cuotas, transferencia bancaria y medios locales (p. ej. en Colombia: Nequi, Daviplata). | Decidido: pasarela principal en Colombia (PSE, tarjetas, Nequi y Daviplata) y pasarela de cobro internacional en USD, elegidas en la fase de diseño (sección 7.1, punto 9). |
| Servicio de autenticación | Servicio que ofrece recuperación de contraseña, autenticación multifactor, bloqueo de cuenta tras intentos fallidos (con opción de desbloqueo) y cierre de sesión tras un tiempo de inactividad. | Decidido: autenticación multifactor con aplicación autenticadora y correo electrónico como alternativa. |
| Sistema de facturación electrónica | Sistema externo integrado vía API para emitir el documento fiscal de cada venta según las reglas del país de origen (en ventas al exterior, factura de exportación) y, si el negocio llegara a tener obligaciones fiscales en otro país, según las de ese país (adaptador por país). | Decidido: proveedor tecnológico habilitado por la DIAN, elegido en la fase de diseño (sección 7.1, punto 10). |
| Servicio de tipos de cambio | Fuente de tasas de cambio entre monedas. | Decidido: servicio externo con actualización diaria y carga manual de contingencia (sección 7.1, punto 5). |
| Transportadora / operador logístico | Sistema externo integrado vía API para cotizar el envío, generar la guía y entregar el seguimiento de cada envío nacional e internacional (adaptador por transportadora). | Decidido: un operador express internacional y un operador nacional, elegidos en la fase de diseño (sección 7.1, punto 6). |
| Agente de aduanas / servicio de cálculo de aranceles | Agente o servicio que apoya la clasificación arancelaria, el cálculo de aranceles e impuestos de importación y la documentación aduanera, tanto en las ventas al exterior como en las importaciones. | Decidido: el operador express despacha los envíos pequeños, un agente de aduanas atiende los de mayor valor y el cálculo usa tablas propias parametrizadas (sección 7.1, puntos 7 y 8). |
| Plataforma de comercio electrónico | Canal en línea para ventas y control de stock, complementario al módulo de gestión interna. | Decidido: es parte del mismo sistema; comparte base de datos y reglas con la gestión interna. |

### Partes interesadas adicionales

- Propietario o responsable del negocio.
- Personal que administra inventario.
- Personal de ventas.
- Personal técnico/mantenimiento del sistema.
- Responsables contables o administrativos, si requieren información del sistema.
- Entidades regulatorias y fiscales de cada país donde se vende o se compra, dado que se deben aplicar impuestos y normas según la legislación del país donde se efectúa cada compra, incluidas las ventas internacionales en línea.
- Autoridades aduaneras de los países por los que se envía o se importa mercancía.

---

# 3. Requisitos funcionales

Los siguientes requisitos incorporan las respuestas confirmadas por el cliente y las decisiones del equipo del proyecto (estado "Decidido").

## RF-001 — Gestión del catálogo de productos

| Campo | Información |  |  |
| ----- | ----- | :---- | :---- |
| **Título de requerimiento** | Gestión del catálogo de productos |  |  |
| **Código de identificación** | RF-001 |  |  |
| **Versión** | 0.4 |  |  |
| **Fuente** | Confirmado con el responsable del negocio. |  |  |
| **Descripción** | El sistema deberá permitir registrar, consultar, modificar y, cuando corresponda, desactivar productos comercializados por la tienda: videoconsolas, videojuegos, tarjetas de regalo, tarjetas de suscripción, ediciones especiales, productos retro y accesorios, organizados por categoría y subcategoría (marca, tipo First/Third Party, tipo de edición, modelo de consola, año, etc.). Los tipos de producto, sus atributos y las categorías son configurables (RF-020), y los nombres y descripciones pueden registrarse en varios idiomas (RF-023). |  |  |
| **Justificación** | Mantener un catálogo único que permita identificar los productos y relacionarlos con el inventario. |  |  |
| **Precondiciones** | El usuario deberá contar con permisos suficientes. |  |  |
| **Restricciones** | Cada producto se identifica por un código interno único. Atributos obligatorios comunes: nombre, categoría, subcategoría y precio (definido por moneda o lista de precios, RF-022). Videoconsola: marca (decidido: modelo y estado nuevo/usado). Videojuego: plataforma, formato (físico/digital), estado (nuevo/usado) y distribuidor. Tarjeta de regalo (decidido): plataforma o tienda, valor nominal y formato. Tarjeta de suscripción (decidido): servicio, plataforma y duración. Accesorio (decidido): marca, tipo y plataforma compatible. Producto retro (decidido): plataforma o consola, año y estado. Edición especial (decidido): plataforma, tipo de edición, formato, estado y distribuidor. Un videojuego físico y uno digital son productos distintos. Todo producto físico (decidido) registra además peso, dimensiones, país de origen y código arancelario (Sistema Armonizado), necesarios para el envío y la aduana (RF-024 y RF-025). Estos atributos son los valores por defecto y se pueden ajustar por configuración. |  |  |
| **Prioridad** | Alta |  |  |
| **Dificultad** | Media |  |  |
| **Dependencia** | Reglas de identificación del producto (código interno único), RF-020 y RF-023. |  |  |
| **Actores** | Administrador, encargado de inventario. |  |  |
| **Entradas** | Datos obligatorios según el tipo de producto, metadatos opcionales, categoría/subcategoría, imágenes. |  |  |
| **Proceso** | Validar datos, crear/actualizar la ficha del producto y conservar su estado. |  |  |
| **Salida** | Producto registrado o información actualizada. |  |  |
| **Postcondiciones** | El catálogo queda actualizado y el producto puede ser consultado. |  |  |
| **Criterios de aceptación** | El usuario autorizado puede crear y consultar un producto con su código interno único; el sistema rechaza datos inválidos; no se crean duplicados; el sistema exige los atributos obligatorios del tipo de producto; no se publica para venta internacional un producto físico sin código arancelario, peso y dimensiones. |  |  |
| **Requerimientos no funcionales** | Usabilidad, integridad de datos, seguridad, rendimiento e internacionalización. |  |  |
| **Estado** | Confirmado; los atributos de tarjetas, accesorios, retro, edición especial y logísticos son decididos (sección 7.1). |  |  |
| **Observaciones** | Metadatos opcionales: género, clasificación por edad (según el sistema de clasificación del país, p. ej. ESRB o PEGI), desarrollador, editor y región. |  |  |

## RF-002 — Consulta de inventario

| Campo | Información |  |  |
| ----- | ----- | :---- | :---- |
| **Título de requerimiento** | Consulta de existencias |  |  |
| **Código de identificación** | RF-002 |  |  |
| **Versión** | 0.4 |  |  |
| **Fuente** | Confirmado con el responsable del negocio. |  |  |
| **Descripción** | El sistema deberá permitir consultar las existencias de los productos registrados y mostrar su estado de inventario. |  |  |
| **Justificación** | Facilitar el control de disponibilidad y detección de faltantes. |  |  |
| **Precondiciones** | Deben existir productos e información de inventario. |  |  |
| **Restricciones** | Existe stock mínimo y máximo por producto, con alertas de bajo inventario (confirmado; ver RF-018). |  |  |
| **Prioridad** | Alta |  |  |
| **Dificultad** | Baja |  |  |
| **Dependencia** | RF-001. |  |  |
| **Actores** | Administrador, encargado de inventario, vendedor/cajero. |  |  |
| **Entradas** | Criterios de búsqueda/filtros. |  |  |
| **Proceso** | Buscar productos y recuperar sus cantidades y estados. |  |  |
| **Salida** | Lista de productos con información de existencias. |  |  |
| **Postcondiciones** | No modifica el inventario. |  |  |
| **Criterios de aceptación** | Un usuario autorizado puede buscar un producto y obtener su existencia actual sin modificarla. |  |  |
| **Requerimientos no funcionales** | Rendimiento, usabilidad y disponibilidad. |  |  |
| **Estado** | Confirmado. |  |  |
| **Observaciones** | Se confirma una sola tienda física, con varias bodegas internas organizadas por categoría/subcategoría (no hay múltiples sucursales). Las ventas a otros países se realizan en línea desde esa tienda. Las preventas y reservas se manejan como stock reservado, separado del disponible (ver RF-019). |  |  |

## RF-003 — Registro de entradas de inventario

| Campo | Información |  |  |
| ----- | ----- | :---- | :---- |
| **Título de requerimiento** | Registro de entrada de mercancía |  |  |
| **Código de identificación** | RF-003 |  |  |
| **Versión** | 0.4 |  |  |
| **Fuente** | Confirmado con el responsable del negocio. |  |  |
| **Descripción** | El sistema deberá permitir registrar entradas de productos al inventario, asociadas a un proveedor oficial, su costo de adquisición y el documento/factura que evidencie la compra. |  |  |
| **Justificación** | Mantener trazabilidad de los aumentos de inventario. |  |  |
| **Precondiciones** | Producto existente o posibilidad de crearlo según la política definida; usuario autorizado. |  |  |
| **Restricciones** | Se debe registrar el proveedor oficial, el costo de adquisición (en la moneda de la compra y su equivalente en la moneda base, RF-022) y el número de factura/documento; se debe controlar la recepción parcial de compras. En compras a proveedores de otro país, el costo de adquisición incluye los costos de importación (RF-026). |  |  |
| **Prioridad** | Alta |  |  |
| **Dificultad** | Media |  |  |
| **Dependencia** | RF-001, RF-009, RF-022, RF-026. |  |  |
| **Actores** | Administrador, encargado de inventario. |  |  |
| **Entradas** | Productos, cantidades y datos de recepción (proveedor, costo, documento). |  |  |
| **Proceso** | Validar la entrada, registrar el movimiento y actualizar las existencias. |  |  |
| **Salida** | Movimiento de entrada y nuevo saldo de inventario. |  |  |
| **Postcondiciones** | El inventario refleja la entrada registrada. |  |  |
| **Criterios de aceptación** | Una entrada válida incrementa el inventario; una entrada inválida no altera el saldo; queda registrado quién y cuándo realizó la operación. |  |  |
| **Requerimientos no funcionales** | Integridad, trazabilidad, seguridad y rendimiento. |  |  |
| **Estado** | Confirmado. |  |  |
| **Observaciones** | Se confirma que existen compras a proveedores oficiales (con recepción parcial) y recompra de productos usados (ver RF-016). |  |  |

## RF-004 — Registro de salidas y movimientos de inventario

| Campo | Información |  |  |
| ----- | ----- | :---- | :---- |
| **Título de requerimiento** | Registro de salidas y movimientos |  |  |
| **Código de identificación** | RF-004 |  |  |
| **Versión** | 0.3 |  |  |
| **Fuente** | Confirmado con el responsable del negocio. |  |  |
| **Descripción** | El sistema deberá registrar las operaciones que disminuyan o modifiquen las existencias: ventas (solo tras confirmación oficial), traslados entre bodegas (RF-017), pérdidas/daños/robos/defectos (con reporte al proveedor oficial), devoluciones de clientes (tras inspección) y ajustes internos aprobados. |  |  |
| **Justificación** | Evitar diferencias entre el inventario registrado y el inventario real. |  |  |
| **Precondiciones** | Usuario autorizado y producto existente. |  |  |
| **Restricciones** | El inventario nunca queda negativo ni con valores decimales. |  |  |
| **Prioridad** | Alta |  |  |
| **Dificultad** | Media |  |  |
| **Dependencia** | RF-001, RF-002 y RF-005. |  |  |
| **Actores** | Administrador, encargado de inventario, vendedor/cajero. |  |  |
| **Entradas** | Producto, cantidad, tipo de movimiento y datos adicionales requeridos. |  |  |
| **Proceso** | Validar operación, registrar movimiento y actualizar existencias. |  |  |
| **Salida** | Movimiento registrado y saldo actualizado. |  |  |
| **Postcondiciones** | El saldo de inventario refleja la operación. |  |  |
| **Criterios de aceptación** | Cada movimiento válido modifica el saldo de acuerdo con su tipo y queda asociado al usuario, fecha y producto. |  |  |
| **Requerimientos no funcionales** | Integridad, trazabilidad y seguridad. |  |  |
| **Estado** | Confirmado. |  |  |
| **Observaciones** | Confirmados como tipos de movimiento: ventas, pérdidas/daños/robos/defectos, traslados entre bodegas, devoluciones de clientes y ajustes manuales. No se mencionan préstamos de productos. |  |  |

## RF-005 — Ajuste y conteo físico de inventario

| Campo | Información |  |  |
| ----- | ----- | :---- | :---- |
| **Título de requerimiento** | Ajuste de inventario por conteo físico |  |  |
| **Código de identificación** | RF-005 |  |  |
| **Versión** | 0.3 |  |  |
| **Fuente** | Confirmado con el responsable del negocio. |  |  |
| **Descripción** | El sistema deberá permitir realizar conteos físicos periódicos y registrar diferencias frente al stock registrado (menos stock sin venta asociada, o más stock sin compra/recompra asociada). |  |  |
| **Justificación** | Controlar discrepancias de inventario. |  |  |
| **Precondiciones** | Usuario con permiso de ajuste. |  |  |
| **Restricciones** | Todo ajuste requiere aprobación del administrador; los ajustes que afectan al negocio requieren también al encargado de inventario, y los ajustes de nivel técnico los realiza solo el experto técnico. |  |  |
| **Prioridad** | Alta |  |  |
| **Dificultad** | Media |  |  |
| **Dependencia** | RF-002 y RF-004. |  |  |
| **Actores** | Encargado de inventario, experto técnico, administrador. |  |  |
| **Entradas** | Conteo físico y observaciones justificativas. |  |  |
| **Proceso** | Comparar cantidades, calcular diferencia y aplicar el ajuste autorizado. |  |  |
| **Salida** | Resultado del conteo y movimiento de ajuste. |  |  |
| **Postcondiciones** | Inventario actualizado si el ajuste fue aprobado. |  |  |
| **Criterios de aceptación** | El sistema conserva el valor anterior, el valor contado, la diferencia, el responsable y la fecha del ajuste; ningún ajuste se refleja en el stock sin la aprobación del administrador. |  |  |
| **Requerimientos no funcionales** | Integridad, trazabilidad, seguridad y auditabilidad. |  |  |
| **Estado** | Confirmado. |  |  |
| **Observaciones** | El rol "Experto técnico" está definido en la matriz de roles de RF-006. |  |  |

## RF-006 — Gestión de usuarios y permisos

| Campo | Información |  |  |
| ----- | ----- | :---- | :---- |
| **Título de requerimiento** | Gestión de usuarios, roles y permisos |  |  |
| **Código de identificación** | RF-006 |  |  |
| **Versión** | 0.4 |  |  |
| **Fuente** | Confirmado con el responsable del negocio. |  |  |
| **Descripción** | El sistema deberá permitir autenticar usuarios mediante recuperación de contraseña y autenticación multifactor, controlar el acceso a funciones según roles, bloquear una cuenta tras un número configurable de intentos fallidos (por defecto 5, con opción de desbloqueo por el administrador) y cerrar automáticamente una sesión tras un tiempo de inactividad configurable (por defecto 1 hora). |  |  |
| **Justificación** | Proteger información y evitar operaciones no autorizadas. |  |  |
| **Precondiciones** | Debe existir un mecanismo de autenticación definido. |  |  |
| **Restricciones** | Se requiere autenticación multifactor (aplicación autenticadora, con correo como alternativa) y recuperación de contraseña por correo (decidido). Un usuario puede tener varios roles. Los roles y sus permisos son configurables (RF-020). |  |  |
| **Prioridad** | Alta |  |  |
| **Dificultad** | Media |  |  |
| **Dependencia** | Matriz de roles y políticas de seguridad; RF-020. |  |  |
| **Actores** | Administrador, usuarios del sistema. |  |  |
| **Entradas** | Credenciales y acciones solicitadas. |  |  |
| **Proceso** | Autenticar, autorizar y registrar las operaciones relevantes. |  |  |
| **Salida** | Acceso concedido o denegado. |  |  |
| **Postcondiciones** | El sistema mantiene una sesión o contexto de autorización válido. |  |  |
| **Criterios de aceptación** | Un usuario sin permiso no puede ejecutar una operación restringida; ninguna sesión permanece activa tras el tiempo de inactividad configurado; una cuenta se bloquea tras el número de intentos fallidos configurado y puede desbloquearse. |  |  |
| **Requerimientos no funcionales** | Seguridad, confidencialidad, integridad y facilidad de uso. |  |  |
| **Estado** | Confirmado. |  |  |
| **Observaciones** | Roles y permisos definidos en la matriz siguiente (decidido). |  |  |

### Matriz de roles y permisos (RF-006)

| Operación | Administrador | Encargado de inventario | Experto técnico | Vendedor/cajero | Gerente/propietario |
|---|---|---|---|---|---|
| Gestionar usuarios, roles y configuración | ✔ | — | — | — | — |
| Gestionar parametrización (países, monedas, impuestos, aranceles, envíos, reglas) | ✔ | — | — | — | — |
| Gestionar tipos de cambio | ✔ | — | — | — | — |
| Crear, modificar y desactivar productos | ✔ | ✔ | — | — | — |
| Consultar catálogo e inventario | ✔ | ✔ | ✔ | ✔ | ✔ |
| Registrar entradas y órdenes de compra | ✔ | ✔ | — | — | — |
| Gestionar importaciones y documentación aduanera | ✔ | ✔ | — | — | Lectura |
| Gestionar proveedores | ✔ | ✔ | — | — | Lectura |
| Registrar ventas y cobros | ✔ | — | — | ✔ | — |
| Anular una venta (sin pago recibido) | ✔ | — | — | ✔ | — |
| Gestionar envíos y despachos | ✔ | ✔ | — | Lectura | — |
| Registrar devolución de cliente | ✔ | — | — | ✔ | — |
| Inspeccionar devoluciones y productos de recompra | — | — | ✔ | — | — |
| Autorizar recompra y fijar su precio | ✔ | — | — | — | — |
| Traslados entre bodegas | ✔ | ✔ | — | — | — |
| Conteo físico y solicitud de ajuste | — | ✔ | ✔ (ajustes técnicos) | — | — |
| Aprobar ajustes de inventario | ✔ | — | — | — | — |
| Diagnóstico, reparación y garantías | Lectura | Lectura | ✔ | Lectura | — |
| Gestionar clientes | ✔ | — | — | ✔ | — |
| Crear promociones y descuentos | ✔ | — | — | — | — |
| Reportes | ✔ | Solo inventario | — | — | ✔ |
| Auditoría (solo lectura) | ✔ | Solo inventario | Solo servicio técnico | — | — |

Un usuario puede tener varios roles; sus permisos son la suma de los de sus roles. Los permisos de un usuario pueden limitarse a uno o varios países; sin esa limitación, aplican a todos los países del negocio.

## RF-007 — Consulta y trazabilidad de movimientos

| Campo | Información |  |  |
| ----- | ----- | :---- | :---- |
| **Título de requerimiento** | Historial de movimientos de inventario |  |  |
| **Código de identificación** | RF-007 |  |  |
| **Versión** | 0.3 |  |  |
| **Fuente** | Confirmado con el responsable del negocio. |  |  |
| **Descripción** | El sistema deberá permitir consultar el historial de movimientos asociados a productos y mostrar la información de trazabilidad definida. |  |  |
| **Justificación** | Facilitar auditoría y análisis de diferencias. |  |  |
| **Precondiciones** | Deben existir movimientos registrados. |  |  |
| **Restricciones** | Se debe auditar el 100% de las operaciones. El histórico de movimientos se conserva por un máximo de 8 años; los registros de auditoría se conservan un mínimo de 10 años antes de poder eliminarse (valores por defecto parametrizables, sin bajar del mínimo legal del país). Depurar el detalle de un movimiento a los 8 años no elimina su registro de auditoría. Las fechas se guardan en UTC y se muestran en la zona horaria configurada. |  |  |
| **Prioridad** | Alta |  |  |
| **Dificultad** | Media |  |  |
| **Dependencia** | RF-003, RF-004 y RF-005. |  |  |
| **Actores** | Administrador, encargado de inventario, experto técnico, gerente/propietario. |  |  |
| **Entradas** | Filtros por producto, tipo de movimiento, usuario y rango temporal. |  |  |
| **Proceso** | Consultar y filtrar movimientos. |  |  |
| **Salida** | Historial de movimientos. |  |  |
| **Postcondiciones** | No modifica datos. |  |  |
| **Criterios de aceptación** | Una consulta autorizada permite identificar producto, tipo de movimiento, cantidad, fecha y usuario. |  |  |
| **Requerimientos no funcionales** | Rendimiento, seguridad, trazabilidad y usabilidad. |  |  |
| **Estado** | Confirmado. |  |  |
| **Observaciones** | Los registros de auditoría no pueden eliminarse antes de cumplir el plazo mínimo (10 años); solo pueden ser consultados (sin modificar) por el administrador, y consultados por el encargado de inventario o el experto técnico dentro de su área. |  |  |

## RF-008 — Reportes de inventario

| Campo | Información |  |  |
| ----- | ----- | :---- | :---- |
| **Título de requerimiento** | Generación de reportes de inventario |  |  |
| **Código de identificación** | RF-008 |  |  |
| **Versión** | 0.4 |  |  |
| **Fuente** | Confirmado con el responsable del negocio. |  |  |
| **Descripción** | El sistema deberá generar reportes de existencias actuales, movimientos, productos de baja rotación, ventas y compras (incluidos sus costos de envío, aranceles e impuestos de importación), todos obligatorios, con filtros por periodo, país, moneda y demás criterios disponibles, exportables en CSV o Excel (recibo de venta/compra en PDF). Los importes se presentan en la moneda de la transacción y en la moneda base, y las fechas, números y monedas siguen el formato del país e idioma del usuario. |  |  |
| **Justificación** | Apoyar control operativo y toma de decisiones. |  |  |
| **Precondiciones** | Existencia de información suficiente y permisos. |  |  |
| **Restricciones** | Todos los reportes listados son obligatorios; el formato de exportación depende del tipo de reporte. |  |  |
| **Prioridad** | Alta |  |  |
| **Dificultad** | Media |  |  |
| **Dependencia** | RF-001 a RF-007 según reporte; RF-022 a RF-026. |  |  |
| **Actores** | Administrador, gerente/propietario y otros roles autorizados. |  |  |
| **Entradas** | Filtros y periodo del reporte. |  |  |
| **Proceso** | Consultar, consolidar y presentar información. |  |  |
| **Salida** | Reporte visual y/o archivo exportable. |  |  |
| **Postcondiciones** | No altera inventario. |  |  |
| **Criterios de aceptación** | Cada reporte presenta información consistente con los datos almacenados, respeta permisos y se exporta en el formato definido. |  |  |
| **Requerimientos no funcionales** | Rendimiento, usabilidad y seguridad. |  |  |
| **Estado** | Confirmado. |  |  |
| **Observaciones** | — |  |  |

---

# 4. Requisitos funcionales adicionales que dependen del alcance

RF-009 a RF-014 tienen alcance confirmado por el cliente; se conserva su numeración original. RF-015 a RF-026 son nuevos y tienen estado "Decidido".

## RF-009 — Gestión de proveedores

| Título de requerimiento | Gestión de proveedores |
| :---- | :---- |
| **Código de identificación** | RF-009 |
| **Tipo de requerimiento** | Funcional |
| **Versión** | 0.5 |
| **Descripción** | Registrar y consultar proveedores oficiales por marca/producto (p. ej. Sony PlayStation para productos PlayStation) y relacionarlos con órdenes de compra, comparación de costos y devoluciones a proveedor. De cada proveedor se registran: razón social, país, identificación tributaria, persona de contacto, correo, teléfono, moneda de compra, condiciones de pago y, si es extranjero, sus condiciones de entrega (Incoterm). Los proveedores solo se registran como dato (sin acceso al sistema ni integración). |
| **Prioridad** | Alta |
| **Criterios de aceptación** | Solo se registran proveedores oficiales; un producto puede tener varios proveedores según la red oficial disponible; se puede comparar el costo entre proveedores (convertido a la moneda base y con sus costos de importación). |

## RF-010 — Registro de ventas

| Título de requerimiento | Registro de ventas |
| :---- | :---- |
| **Código de identificación** | RF-010 |
| **Tipo de requerimiento** | Funcional |
| **Versión** | 0.4 |
| **Descripción** | Registrar ventas y reflejar sus efectos en el inventario, ya que el sistema opera también como punto de venta. Soportar los medios de pago habilitados en el país de la venta (efectivo, cuotas, transferencia bancaria y medios locales, p. ej. Nequi y Daviplata en Colombia); descuentos por promociones cuando correspondan; cálculo de impuestos según el país de la transacción (evidenciados al cliente en compras internacionales en línea); ventas a crédito; y generación del documento fiscal electrónico. En ventas en línea a otro país, incluir el costo de envío (RF-024) y el costo estimado de importación: aranceles e impuestos de importación (RF-025). Cada venta registra su moneda, el tipo de cambio aplicado y los impuestos calculados. Las reglas de crédito, promociones, envíos y estados se detallan en "Reglas complementarias" (antes de RF-015). |
| **Prioridad** | Alta |
| **Criterios de aceptación** | El inventario solo se descuenta tras la confirmación oficial de la venta; antes de confirmar una venta internacional el cliente ve el total estimado con producto, envío, impuestos y aranceles, o la indicación de que los paga al recibir; toda venta genera su documento fiscal electrónico (en ventas al exterior, factura de exportación); el total de la venta se muestra en la moneda del cliente. |

## RF-011 — Gestión de devoluciones

| Título de requerimiento | Gestión de devoluciones |
| :---- | :---- |
| **Código de identificación** | RF-011 |
| **Tipo de requerimiento** | Funcional |
| **Versión** | 0.4 |
| **Descripción** | Registrar devoluciones de clientes (físicas y virtuales) y devoluciones a proveedores, determinando su efecto sobre el inventario. Permitir anular una venta mientras el pago no se haya recibido. Los plazos de devolución y de retracto en compras en línea se parametrizan por país (RF-021). En devoluciones internacionales se registran el costo del envío de retorno, el trámite aduanero de reingreso y, cuando la norma lo permita, la devolución de impuestos y aranceles pagados. |
| **Prioridad** | Alta |
| **Criterios de aceptación** | Toda devolución de cliente requiere inspección previa; se rechaza si el producto está dañado, defectuoso o manipulado. Una venta solo puede anularse si el pago no ha sido recibido. Una devolución internacional queda asociada a su envío de retorno y a su documentación aduanera. |

## RF-012 — Gestión de números de serie

| Título de requerimiento | Gestión de productos serializados |
| :---- | :---- |
| **Código de identificación** | RF-012 |
| **Tipo de requerimiento** | Funcional |
| **Versión** | 0.2 |
| **Descripción** | Registrar y rastrear unidades individuales mediante número de serie, dado que el negocio confirma el control unitario por número de serie, incluido su uso en el historial de servicio y garantía. |
| **Prioridad** | Alta |
| **Criterios de aceptación** | Todo producto con número de serie queda identificado de forma individual y trazable desde su entrada hasta su salida o servicio. |

## RF-013 — Gestión de garantías y servicio técnico

| Título de requerimiento | Gestión de garantías y servicio técnico |
| :---- | :---- |
| **Código de identificación** | RF-013 |
| **Tipo de requerimiento** | Funcional |
| **Versión** | 0.2 |
| **Descripción** | Registrar la garantía de fabricante de cada producto (fecha de inicio y vencimiento), las reparaciones y diagnósticos realizados, y mantener un historial de servicio por número de serie. Los productos defectuosos deben separarse del inventario disponible mientras están en reparación o mantenimiento. Los plazos de garantía legal que exija cada país se parametrizan (RF-021). |
| **Prioridad** | Alta |
| **Criterios de aceptación** | Un producto en reparación no aparece como disponible para la venta; la garantía registrada corresponde siempre al fabricante. |

## RF-014 — Canal de comercio electrónico

| Título de requerimiento | Canal de comercio electrónico |
| :---- | :---- |
| **Código de identificación** | RF-014 |
| **Tipo de requerimiento** | Funcional |
| **Versión** | 0.4 |
| **Descripción** | El sistema deberá operar tanto mediante un canal de comercio electrónico (ventas y control de stock en línea) como mediante un módulo de gestión interna (ventas, reposición de stock, recompras aceptadas, mantenimiento, etc.), integrándose vía API con sistemas externos de facturación electrónica, pagos, transporte y aduanas. El canal en línea forma parte del mismo sistema y comparte base de datos y reglas con la gestión interna. Vende, desde una sola tienda, a clientes de los países habilitados (RF-021), mostrando precios en la moneda del cliente (RF-022), la interfaz en su idioma (RF-023) y el costo total de la compra con envío, impuestos y aranceles (RF-024 y RF-025). |
| **Prioridad** | Alta |
| **Criterios de aceptación** | El sistema permite completar el ciclo de venta tanto en el canal físico como en línea, aplicando las mismas reglas de inventario, impuestos y facturación; un cliente de un país habilitado ve precios, impuestos, envío y textos en su moneda e idioma. |

---

## Reglas complementarias de ventas, compras, envíos y devoluciones (decidido)

Todos los valores de esta sección son valores por defecto y se configuran para todo el negocio o por país (RF-020).

**Ventas a crédito y cuotas**

- Solo para clientes frecuentes y con aprobación del administrador.
- Cuota inicial mínima del 30%; plazos de 3, 6 o 12 cuotas mensuales.
- La tasa la define el administrador y no puede superar el límite legal vigente del país (en Colombia, la tasa de usura).
- La venta a crédito se confirma al recibir la cuota inicial.

**Promociones y descuentos**

- Tipos: porcentaje, 2x1, por temporada y combo.
- Las crea el administrador, con fecha de inicio y fin.
- No se acumulan: una promoción por producto a la vez.
- El precio final no puede quedar por debajo del costo de adquisición sin aprobación del administrador.

**Envíos y costos de importación del cliente (DAP por defecto, confirmado)**

- Por defecto, el cliente paga los aranceles e impuestos de importación al recibir el pedido (condición DAP). El sistema los estima y los muestra antes de confirmar la compra. Por país se puede configurar que el negocio los pague (condición DDP).
- El costo de envío se calcula por peso, dimensiones y zona de destino, con la tarifa de la transportadora.
- Los productos digitales (videojuego digital, tarjetas de regalo y de suscripción) no tienen envío ni trámite aduanero, pero generan impuestos según el país del cliente.

**Estados**

| Entidad | Estados |
|---|---|
| Venta | Pendiente de pago → Confirmada → Devuelta parcial / Devuelta total. Anulada (solo desde Pendiente de pago). |
| Pago | Sin pago → Parcial (crédito) → Pagado |
| Envío | Pendiente de despacho → En preparación → Despachado → En tránsito → En aduana (solo envíos internacionales) → Entregado / Devuelto al remitente |
| Compra | Borrador → Enviada al proveedor → En tránsito → En aduana (solo importaciones) → Recibida parcial → Recibida completa. Cancelada (solo si no se ha recibido nada). |
| Devolución de cliente | Solicitada → En inspección → Aceptada / Rechazada → Cerrada |
| Devolución a proveedor | Solicitada → Enviada → Cerrada |
| Reserva o preventa | Activa → Pagada y entregada / Vencida / Cancelada |

## RF-015 — Gestión de clientes

| Título de requerimiento | Gestión de clientes |
| :---- | :---- |
| **Código de identificación** | RF-015 |
| **Tipo de requerimiento** | Funcional |
| **Versión** | 0.1 |
| **Descripción** | Registrar y consultar clientes de cualquier país habilitado. Frecuente (más de dos compras, umbral configurable): nombre, apellidos, código postal, dirección, teléfono y correo, conservado de forma permanente. Infrecuente (una o dos compras): nombre, documento y correo (necesarios para el documento fiscal), conservado 12 meses desde su última compra (configurable). El formato de dirección, teléfono y el tipo de documento de identidad dependen del país del cliente (RF-021). |
| **Prioridad** | Alta |
| **Estado** | Decidido. |
| **Criterios de aceptación** | El sistema reclasifica automáticamente al cliente al superar el umbral de compras; pasado el plazo se eliminan los datos de contacto del infrecuente; las facturas conservan sus datos fiscales; se registra la autorización de tratamiento de datos (RNF-014). |

## RF-016 — Recompra de productos usados

| Título de requerimiento | Recompra de productos usados |
| :---- | :---- |
| **Código de identificación** | RF-016 |
| **Tipo de requerimiento** | Funcional |
| **Versión** | 0.1 |
| **Descripción** | Registrar la compra de productos usados a clientes. El administrador autoriza y fija el precio; el experto técnico revisa funcionamiento, estado físico, número de serie y accesorios; se registran nombre y documento del vendedor. El precio de recompra no supera el 50% del precio de venta de usado vigente (configurable). Al aceptarse, el producto ingresa al inventario como usado con su número de serie. |
| **Prioridad** | Media |
| **Estado** | Decidido. |
| **Criterios de aceptación** | No se acepta una recompra sin autorización del administrador, revisión técnica e identificación del vendedor; el producto aceptado queda trazable por número de serie. |

## RF-017 — Traslados entre bodegas

| Título de requerimiento | Traslados entre bodegas |
| :---- | :---- |
| **Código de identificación** | RF-017 |
| **Tipo de requerimiento** | Funcional |
| **Versión** | 0.1 |
| **Descripción** | Mover existencias de una bodega a otra dentro de la tienda. Lo realiza el administrador o el encargado de inventario, sin aprobación adicional, y queda registrado con origen, destino, cantidad, usuario y fecha. |
| **Prioridad** | Media |
| **Estado** | Decidido. |
| **Criterios de aceptación** | El traslado no cambia el total del producto; no se puede trasladar más de lo disponible en la bodega de origen. |

## RF-018 — Alertas de stock mínimo y máximo

| Título de requerimiento | Alertas de stock |
| :---- | :---- |
| **Código de identificación** | RF-018 |
| **Tipo de requerimiento** | Funcional |
| **Versión** | 0.1 |
| **Descripción** | Cada producto tiene stock mínimo y máximo (con valor inicial sugerido por categoría). Cuando el stock llega al mínimo o supera el máximo, el sistema avisa en el panel y por correo al encargado de inventario y al administrador. |
| **Prioridad** | Media |
| **Estado** | Decidido. |
| **Criterios de aceptación** | La alerta se genera al cruzar el umbral y no se repite mientras la situación no cambie. |

## RF-019 — Preventas y reservas

| Título de requerimiento | Preventas y reservas |
| :---- | :---- |
| **Código de identificación** | RF-019 |
| **Tipo de requerimiento** | Funcional |
| **Versión** | 0.2 |
| **Descripción** | Preventa (producto aún no disponible): el cliente aparta con un anticipo mínimo del 30% y la reserva se mantiene hasta la fecha de lanzamiento. Apartado (producto en stock): el stock se separa del disponible por 15 días; si vence sin pago, se libera. Anticipos: si cancela el negocio o no entrega, se devuelve el 100%; si cancela el cliente dentro del plazo de retracto del país, se devuelve el 100%; fuera de ese plazo se retiene hasta el 10% y se devuelve el resto. Valores configurables. |
| **Prioridad** | Media |
| **Estado** | Decidido. |
| **Criterios de aceptación** | El stock reservado no aparece como disponible para la venta; al vencer una reserva sin pago el stock se libera automáticamente; toda cancelación calcula y registra la devolución del anticipo según la política. |

## RF-020 — Parametrización general del sistema

| Título de requerimiento | Parametrización general |
| :---- | :---- |
| **Código de identificación** | RF-020 |
| **Tipo de requerimiento** | Funcional |
| **Versión** | 0.2 |
| **Descripción** | El sistema deberá permitir al administrador configurar, sin modificar el código ni reiniciar el servicio, las reglas y catálogos del negocio: tipos de producto y sus atributos (obligatorios y opcionales); categorías y subcategorías; estados; roles y permisos; umbrales y plazos (clientes frecuentes, retención de clientes, histórico y auditoría, inactividad de sesión, intentos fallidos, escala de reducción de usados, condiciones de crédito, vigencia de apartados, anticipos); medios de pago; promociones; tarifas de envío, aranceles y reglas aduaneras; plantillas de documentos y notificaciones; e integraciones externas. Cada parámetro tiene valor por defecto, ámbito (todo el negocio o un país) y vigencia (fecha desde la cual aplica). |
| **Prioridad** | Alta |
| **Estado** | Decidido. |
| **Criterios de aceptación** | Un cambio de parámetro se aplica sin volver a desplegar el sistema; prevalece el valor del ámbito más específico; las operaciones ya registradas conservan los valores vigentes al momento de realizarse; todo cambio queda auditado con usuario, fecha, valor anterior y valor nuevo; el sistema rechaza valores fuera de rango. |

## RF-021 — Configuración por país

| Título de requerimiento | Configuración por país |
| :---- | :---- |
| **Código de identificación** | RF-021 |
| **Tipo de requerimiento** | Funcional |
| **Versión** | 0.3 |
| **Descripción** | El sistema deberá mantener un catálogo de países habilitados, cada uno con su configuración propia: moneda (RF-022), idiomas (RF-023), zona horaria, formatos de fecha, número, teléfono y dirección, tipos de documento de identidad, impuestos (tipo, tarifa, vigencia, aplicación por categoría de producto, impuesto sobre servicios digitales y si el precio se muestra con impuesto incluido o no), aranceles e impuestos de importación (RF-025), umbral de exención por valor mínimo, productos restringidos o prohibidos, condición de entrega por defecto (DAP o DDP), documentos aduaneros exigidos, métodos y transportadoras de envío (RF-024), documentos fiscales (el del país de origen y, solo si el negocio tiene obligaciones fiscales en un país, el adaptador de ese país con su numeración), medios de pago disponibles, norma de protección de datos aplicable, plazos legales (retención de documentos, devolución y retracto en compras en línea, garantía legal) y sistema de clasificación por edad de los videojuegos. |
| **Prioridad** | Alta |
| **Estado** | Decidido. |
| **Criterios de aceptación** | Habilitar un país con configuración completa no requiere desarrollo, salvo un adaptador nuevo si el negocio debe emitir documentos fiscales en ese país; un país con configuración incompleta no puede habilitarse para ventas; no se permite vender a un país sin tarifa de impuesto registrada. |
| **Observaciones** | País de origen: Colombia. Destinos iniciales y monedas: sección 7.1, puntos 2 y 3. |

## RF-022 — Multimoneda y tipos de cambio

| Título de requerimiento | Multimoneda y tipos de cambio |
| :---- | :---- |
| **Código de identificación** | RF-022 |
| **Tipo de requerimiento** | Funcional |
| **Versión** | 0.3 |
| **Descripción** | El negocio tiene una moneda base para consolidar sus resultados, y cada país opera en su moneda local. Cada moneda se define con su código ISO 4217, nombre, símbolo, número de decimales, formato de presentación y regla de redondeo. Las ventas y compras pueden realizarse en otras monedas. Los tipos de cambio se obtienen por defecto de un servicio externo con actualización diaria, con carga manual de contingencia, y se conservan con su fecha; el administrador puede definir un margen sobre la tasa. Los precios se definen por lista de precios por moneda o por conversión desde la moneda base. |
| **Prioridad** | Alta |
| **Estado** | Decidido. |
| **Criterios de aceptación** | Cada transacción guarda su moneda, el importe, el tipo de cambio aplicado y el equivalente en moneda base; un cambio posterior del tipo de cambio no altera transacciones ya registradas; los importes se muestran con los decimales, símbolo y formato de la moneda; los reportes consolidan en la moneda base; si el servicio de tipos de cambio falla, el sistema usa la última tasa vigente y avisa al administrador. |

## RF-023 — Idiomas y localización de contenido

| Título de requerimiento | Idiomas y localización de contenido |
| :---- | :---- |
| **Código de identificación** | RF-023 |
| **Tipo de requerimiento** | Funcional |
| **Versión** | 0.1 |
| **Descripción** | La interfaz, los mensajes, los documentos (recibos, facturas) y las notificaciones deberán estar disponibles en los idiomas habilitados (por defecto español e inglés). El idioma se define por usuario y, en el canal en línea, por cliente. El catálogo (nombres y descripciones) puede registrarse en varios idiomas. Las fechas, horas, números y monedas se presentan según la localización del usuario. |
| **Prioridad** | Alta |
| **Estado** | Decidido. |
| **Criterios de aceptación** | Agregar un idioma se hace cargando traducciones, sin modificar el código; si un producto no tiene traducción, se muestra el idioma por defecto; un documento emitido usa el idioma y formato correspondientes. |

## RF-024 — Envíos y logística de entrega

| Título de requerimiento | Envíos y logística de entrega |
| :---- | :---- |
| **Código de identificación** | RF-024 |
| **Tipo de requerimiento** | Funcional |
| **Versión** | 0.2 |
| **Descripción** | Gestionar el envío de las ventas en línea de productos físicos, nacionales e internacionales: métodos de envío y transportadoras por país, cálculo del costo según peso, dimensiones y zona de destino, tiempo estimado de entrega, dirección de envío en el formato del país, generación de la guía, seguimiento del envío y registro de sus estados (sección 4, reglas complementarias). Se controlan las restricciones de transporte por tipo de producto (p. ej. productos con baterías) y por país. Se integra un operador express internacional para ventas al exterior y un operador nacional para ventas dentro del país de origen, mediante adaptadores. |
| **Prioridad** | Alta |
| **Estado** | Decidido. |
| **Criterios de aceptación** | El cliente conoce el costo y el tiempo estimado de envío antes de confirmar la compra; cada envío queda asociado a su venta, guía y estado; no se permite despachar un producto con restricción de transporte hacia el país de destino; el encargado de inventario puede consultar y actualizar el estado del despacho. |
| **Observaciones** | Los productos digitales no generan envío. Los operadores se eligen en la fase de diseño (sección 7.1, punto 6). |

## RF-025 — Comercio exterior: aranceles, impuestos de importación y aduanas en ventas

| Título de requerimiento | Aranceles, impuestos de importación y aduanas en ventas |
| :---- | :---- |
| **Código de identificación** | RF-025 |
| **Tipo de requerimiento** | Funcional |
| **Versión** | 0.2 |
| **Descripción** | Para cada venta a un país distinto del de origen, el sistema deberá calcular y mostrar el costo estimado de importación (aranceles e impuestos de importación) según el país de destino, el código arancelario del producto (Sistema Armonizado), su valor en aduana y su país de origen; aplicar el umbral de exención por valor mínimo de cada país; definir quién paga esos costos (por defecto el cliente al recibir, DAP; el negocio, DDP, solo si se configura para un país); bloquear productos restringidos o prohibidos en el país de destino; generar la documentación aduanera exigida (factura comercial o de exportación y datos para la declaración de exportación); y registrar el estado aduanero del envío. El cálculo usa tablas propias parametrizadas (código arancelario por país), con un adaptador para un servicio externo de cálculo en una fase posterior. Los productos digitales no pasan por aduana, pero generan impuestos del país del cliente. |
| **Prioridad** | Alta |
| **Estado** | Decidido. |
| **Criterios de aceptación** | Antes de confirmar la venta, el cliente ve el costo estimado de importación y quién lo paga; el sistema no permite vender un producto restringido o prohibido en el país de destino; cada envío internacional genera su documentación aduanera; el estado aduanero del envío puede consultarse (en aduana, liberado, retenido). |
| **Observaciones** | Las reglas aduaneras y tributarias cambian por país y en el tiempo: se parametrizan (RF-021) y se consideran en la sección 7.2 para una operación real. La tabla de restricciones la mantiene el administrador y se revisa cada 6 meses. |

## RF-026 — Importación en compras a proveedores de otros países

| Título de requerimiento | Importación en compras a proveedores de otros países |
| :---- | :---- |
| **Código de identificación** | RF-026 |
| **Tipo de requerimiento** | Funcional |
| **Versión** | 0.1 |
| **Descripción** | Las órdenes de compra a proveedores de otro país registran la moneda, la condición de entrega (Incoterm), el flete, el seguro, los aranceles, los impuestos de importación, los gastos de agente de aduanas y los documentos de la importación (factura comercial, guía, declaración de importación). La compra pasa por los estados "En tránsito" y "En aduana" antes de su recepción. El costo de adquisición de cada unidad es el costo del proveedor más los costos de importación prorrateados (costo puesto en bodega). Los impuestos recuperables se registran aparte y no suman al costo (según el criterio del contador). |
| **Prioridad** | Alta |
| **Estado** | Decidido. |
| **Criterios de aceptación** | Una compra internacional no puede recibirse sin sus documentos de importación; el costo de adquisición de cada unidad incluye los costos de importación prorrateados; los reportes de compras muestran el costo del proveedor y el costo de importación por separado. |
| **Observaciones** | Alimenta el costo de adquisición de RF-003. |

---

# 5. Reglas de negocio identificadas

Se conserva la numeración original; se actualiza el estado de cada regla y se agregan las reglas nuevas.

| Código | Regla de negocio | Estado |
|---|---|---|
| RN-001 | Un producto deberá poder identificarse de manera inequívoca dentro del catálogo. | Confirmado: cada producto se identifica por su código interno asignado por el sistema. |
| RN-002 | Una operación de inventario deberá estar asociada al producto afectado. | Confirmado. |
| RN-003 | Las modificaciones de existencias deberán generar trazabilidad cuando el negocio requiera auditoría. | Confirmado: toda modificación de existencias conserva trazabilidad completa. |
| RN-004 | Un usuario solo podrá ejecutar operaciones para las que tenga autorización. | Confirmado. Roles y permisos definidos en la matriz de RF-006; un usuario puede tener varios roles. |
| RN-005 | El inventario no deberá permitir cantidades inválidas según las reglas que defina el negocio. | Confirmado: no se permite inventario negativo; las cantidades siempre son números enteros. |
| RN-006 | Los productos descontinuados o inactivos no deberían eliminarse físicamente si existen movimientos históricos que dependan de ellos, salvo que el negocio defina otra política. | Confirmado: un producto solo puede desactivarse, nunca eliminarse. |
| RN-007 | Los ajustes manuales deberán conservar información suficiente para conocer qué se cambió, quién lo hizo y cuándo. | Confirmado: todo ajuste requiere aprobación del administrador. |
| RN-008 | Si existen productos serializados, cada número de serie deberá cumplir una regla de unicidad definida por el negocio. | Confirmado: se manejan productos con número de serie individual. |
| RN-009 | Si existen múltiples sucursales o ubicaciones, las existencias deberán identificarse por ubicación. | Confirmado: existe una sola tienda física, con bodegas internas organizadas por categoría/subcategoría; no hay múltiples sucursales. Las ventas a otros países se realizan en línea desde esa tienda. |
| RN-010 | Los movimientos deberán mantener consistencia entre el detalle de la operación y el saldo de inventario. | Confirmado. |
| RN-011 | Si las ventas forman parte del sistema, una venta confirmada deberá aplicar las reglas de disminución de inventario definidas por el negocio. | Confirmado: la venta confirmada oficialmente descuenta automáticamente el inventario. |
| RN-012 | Si se registran devoluciones, su efecto sobre el inventario dependerá del estado del producto devuelto y de la política comercial. | Confirmado: la devolución de cliente requiere inspección previa; se rechaza si el producto está dañado, defectuoso o manipulado. |
| RN-013 | El precio de un producto usado se reduce respecto al precio de mercado del producto nuevo, según su año; la reducción máxima permitida es del 20%. | Confirmado. Escala decidida (parametrizable): hasta 1 año 5%, 2 años 10%, 3 años 15%, 4 años o más 20% (tope). Los productos retro no aplican la escala. |
| RN-014 | Solo se registran proveedores oficiales por marca/producto. | Confirmado. |
| RN-015 | El impuesto aplicado a una venta depende de la legislación del país de la transacción, incluidas las compras en línea desde el exterior, y debe evidenciarse al cliente antes de confirmar la compra. | Confirmado. Las tarifas y reglas se parametrizan por país (RF-021); ninguna tarifa está fijada en el código. |
| RN-016 | Un registro de auditoría no puede eliminarse antes de cumplir el plazo mínimo de retención establecido (10 años). | Confirmado. 10 años es el valor por defecto; es parametrizable y nunca puede ser menor al mínimo legal del país. |
| RN-017 | El histórico de movimientos de inventario se conserva por un máximo de 8 años, tras lo cual se depura. | Confirmado. 8 años es el valor por defecto, parametrizable; la depuración no elimina el registro de auditoría (RN-016). |
| RN-018 | Un videojuego físico y uno digital son productos distintos (igual criterio para ediciones full digital de consolas). | Confirmado. |
| RN-019 | Solo los clientes frecuentes se registran de forma completa y permanente; los infrecuentes se conservan por un periodo reducido. | Confirmado: el cliente infrecuente se conserva 12 meses desde su última compra (decidido, parametrizable); luego se eliminan sus datos de contacto. |
| RN-020 | Una venta solo puede anularse si el pago aún no ha sido recibido. | Confirmado. |
| RN-021 | El crédito y las cuotas solo se ofrecen a clientes frecuentes y con aprobación del administrador. | Decidido. |
| RN-022 | Un producto tiene una sola promoción activa a la vez; el precio final no baja del costo de adquisición sin aprobación del administrador. | Decidido. |
| RN-023 | Una recompra requiere autorización del administrador, revisión técnica e identificación del vendedor. | Decidido. |
| RN-024 | El stock reservado no está disponible para la venta. | Decidido. |
| RN-025 | No se vende a un país que no esté habilitado o que no tenga tarifa de impuesto registrada. | Decidido. |
| RN-026 | Cada transacción conserva la moneda, el tipo de cambio y los impuestos vigentes al momento de realizarse; cambios posteriores no la modifican. | Decidido. |
| RN-027 | Los valores parametrizables tienen vigencia: una modificación solo aplica a las operaciones posteriores a su fecha de inicio. | Decidido. |
| RN-028 | Toda venta, compra, cliente, envío y documento fiscal registra el país al que corresponde. | Decidido. |
| RN-029 | Antes de confirmar una venta internacional en línea, se muestra al cliente el total estimado con producto, envío, impuestos y aranceles, e indica quién paga los costos de importación (DAP o DDP; por defecto DAP). | Confirmado. |
| RN-030 | No se vende ni se envía un producto a un país donde esté restringido o prohibido su ingreso. | Decidido. |
| RN-031 | El costo de adquisición de un producto importado incluye flete, seguro, aranceles e impuestos no recuperables (costo puesto en bodega). | Decidido. |
| RN-032 | Los productos digitales no generan envío ni trámite aduanero, pero sí impuestos según el país del cliente. | Decidido. |

---

# 6. Requisitos no funcionales / de calidad

ISO/IEC 25010:2023 define un modelo de calidad de producto compuesto por nueve características y subcaracterísticas. Estas características sirven como referencia para especificar y evaluar requisitos de calidad.

Para este sistema se proponen los siguientes requisitos medibles, actualizados con la información confirmada por el cliente y las decisiones del proyecto.

## RNF-001 — Adecuación funcional

| Campo | Información |
|---|---|
| **Título de requerimiento** | Cobertura funcional del inventario |
| **Código de identificación** | RNF-001 |
| **Tipo de requerimiento** | No funcional — calidad |
| **Versión** | 0.1 |
| **Descripción** | El sistema deberá proporcionar las funciones aprobadas para gestionar productos, existencias y movimientos sin omitir reglas de negocio definidas. |
| **Prioridad** | Alta |
| **Criterios de aceptación** | Todas las funciones incluidas en la línea base de alcance tendrán al menos un caso de aceptación verificable y deberán operar conforme a sus reglas definidas. |

## RNF-002 — Rendimiento y eficiencia

| Campo | Información |
|---|---|
| **Título de requerimiento** | Tiempo de respuesta de operaciones |
| **Código de identificación** | RNF-002 |
| **Tipo de requerimiento** | No funcional — rendimiento |
| **Versión** | 0.3 |
| **Descripción** | Las operaciones frecuentes de consulta y actualización deberán responder dentro de los tiempos máximos acordados, considerando un catálogo de hasta 100.000 productos, 10.000 usuarios registrados con picos de hasta 500 usuarios concurrentes en el comercio electrónico y un volumen aproximado de 5-6 movimientos de inventario por día. |
| **Prioridad** | Alta |
| **Criterios de aceptación** | Con la carga indicada: el 95% de las consultas responde en 2 segundos o menos, las operaciones de registro (venta, entrada, ajuste) en 3 segundos o menos y los reportes mensuales en 10 segundos o menos. |

## RNF-003 — Seguridad

| Campo | Información |
|---|---|
| **Título de requerimiento** | Control de acceso y protección de información |
| **Código de identificación** | RNF-003 |
| **Tipo de requerimiento** | No funcional — seguridad |
| **Versión** | 0.3 |
| **Descripción** | El sistema deberá proteger las funciones y datos mediante autenticación (con recuperación de contraseña y autenticación multifactor), autorización y controles de acceso acordes con los roles definidos. Las credenciales no deberán almacenarse en texto plano. Una cuenta se bloquea tras 5 intentos fallidos (el administrador puede desbloquearla) y una sesión se cierra automáticamente tras una hora de inactividad. Ambos valores son parametrizables. |
| **Prioridad** | Alta |
| **Criterios de aceptación** | Las operaciones restringidas serán rechazadas para usuarios no autorizados; las credenciales estarán protegidas; ninguna sesión permanece activa tras el tiempo de inactividad configurado. |

## RNF-004 — Integridad y consistencia de datos

| Campo | Información |
|---|---|
| **Título de requerimiento** | Integridad de inventario |
| **Código de identificación** | RNF-004 |
| **Tipo de requerimiento** | No funcional — calidad de datos / integridad |
| **Versión** | 0.1 |
| **Descripción** | El sistema deberá preservar la consistencia entre productos, movimientos y existencias, evitando registros huérfanos, duplicidades no permitidas y actualizaciones parciales de operaciones críticas. |
| **Prioridad** | Alta |
| **Criterios de aceptación** | Una operación que falle no deberá dejar el inventario en un estado parcialmente actualizado. |

## RNF-005 — Usabilidad / facilidad de uso

| Campo | Información |
|---|---|
| **Título de requerimiento** | Facilidad de uso para operaciones de inventario |
| **Código de identificación** | RNF-005 |
| **Tipo de requerimiento** | No funcional — usabilidad |
| **Versión** | 0.4 |
| **Descripción** | Las tareas frecuentes deberán presentar navegación comprensible, mensajes de error claros y formularios coherentes con las actividades de los usuarios. El sistema deberá mostrar de forma clara al cliente, en su moneda, el costo total estimado de una compra internacional en línea (producto, envío, impuestos y aranceles) antes de confirmarla. |
| **Prioridad** | Alta |
| **Criterios de aceptación** | Las tareas definidas como críticas podrán ser realizadas por usuarios representativos sin depender de conocimientos técnicos; el cliente ve el desglose de impuestos, aranceles y envío y el total en su moneda antes de confirmar cualquier compra internacional en línea. |

## RNF-006 — Mantenibilidad

| Campo | Información |
|---|---|
| **Título de requerimiento** | Facilidad de mantenimiento |
| **Código de identificación** | RNF-006 |
| **Tipo de requerimiento** | No funcional — mantenibilidad |
| **Versión** | 0.3 |
| **Descripción** | El sistema deberá organizarse de forma que cambios en reglas, catálogo, roles o reportes puedan realizarse sin introducir cambios innecesarios en módulos no relacionados. Agregar un país, una moneda o un idioma debe ser un cambio de configuración y no de código. |
| **Prioridad** | Media |
| **Criterios de aceptación** | Los módulos y componentes deberán contar con estructura y documentación técnica suficiente para que otro desarrollador pueda localizar y modificar una función definida. |

## RNF-007 — Fiabilidad

| Campo | Información |
|---|---|
| **Título de requerimiento** | Operación confiable |
| **Código de identificación** | RNF-007 |
| **Tipo de requerimiento** | No funcional — fiabilidad |
| **Versión** | 0.3 |
| **Descripción** | El sistema deberá manejar errores de operación sin corromper los datos y deberá permitir recuperar el servicio en un máximo de 3 horas ante un fallo, conservando el histórico de movimientos 8 años (valor por defecto parametrizable, ver RN-017). |
| **Prioridad** | Alta |
| **Criterios de aceptación** | El tiempo de recuperación ante un fallo no supera las 3 horas. El respaldo cumple lo definido en RNF-011. |

## RNF-008 — Compatibilidad e interoperabilidad

| Campo | Información |
|---|---|
| **Título de requerimiento** | Integración con sistemas externos |
| **Código de identificación** | RNF-008 |
| **Tipo de requerimiento** | No funcional — compatibilidad |
| **Versión** | 0.5 |
| **Descripción** | El sistema deberá integrarse vía API con sistemas externos de facturación electrónica (proveedor autorizado por la autoridad fiscal del país de origen), de pagos (con soporte de los medios locales de cada país y cobro internacional en USD), de transporte (cotización, guía y seguimiento) y de aduanas o cálculo de aranceles. Las integraciones se implementan como adaptadores intercambiables por país y por proveedor, de modo que agregar un proveedor no requiere modificar el núcleo del sistema. El comercio electrónico es parte del mismo sistema. |
| **Prioridad** | Alta |
| **Criterios de aceptación** | El sistema intercambia información correctamente con los sistemas externos de facturación, pagos, transporte y aduanas. Los proveedores se eligen en la fase de diseño con estos criterios: cobertura de los medios de pago locales (PSE, tarjetas, Nequi y Daviplata en Colombia), comisión, manejo de contracargos, conciliación, calidad de la API y, en facturación, soporte de factura electrónica de exportación (sección 7.1, puntos 9 y 10). |

## RNF-009 — Escalabilidad y capacidad

| Campo | Información |
|---|---|
| **Título de requerimiento** | Capacidad del sistema |
| **Código de identificación** | RNF-009 |
| **Tipo de requerimiento** | No funcional — rendimiento/capacidad |
| **Versión** | 0.4 |
| **Descripción** | La solución deberá soportar un catálogo inicial de hasta 100.000 productos, 10.000 usuarios registrados y picos de hasta 500 usuarios concurrentes, manteniendo los objetivos de rendimiento acordados, y crecer hasta 10 veces el volumen inicial de movimientos sin cambios de arquitectura. |
| **Prioridad** | Media |
| **Criterios de aceptación** | El sistema soporta el volumen inicial sin degradar el rendimiento y una prueba de carga con 10 veces el volumen inicial de movimientos cumple los tiempos de RNF-002. |

## RNF-010 — Auditabilidad y trazabilidad

| Campo | Información |
|---|---|
| **Título de requerimiento** | Trazabilidad de operaciones |
| **Código de identificación** | RNF-010 |
| **Tipo de requerimiento** | No funcional — seguridad / mantenibilidad / auditabilidad |
| **Versión** | 0.2 |
| **Descripción** | El sistema deberá conservar evidencia del 100% de las operaciones críticas, identificando como mínimo la operación, fecha/hora (en UTC) y usuario responsable. |
| **Prioridad** | Alta |
| **Criterios de aceptación** | El acceso de consulta a la auditoría se restringe al administrador (solo lectura) y al encargado de inventario o experto técnico dentro de su área; ningún registro se elimina antes de cumplir el plazo mínimo de retención (10 años por defecto). |

## RNF-011 — Recuperación y respaldo

| Campo | Información |
|---|---|
| **Título de requerimiento** | Respaldo y recuperación de información |
| **Código de identificación** | RNF-011 |
| **Tipo de requerimiento** | No funcional — fiabilidad |
| **Versión** | 0.4 |
| **Descripción** | El sistema deberá contar con un mecanismo de respaldo y recuperación de datos, con respaldo automático diario, tiempo máximo de recuperación de 3 horas y pérdida máxima tolerada de 24 horas. |
| **Prioridad** | Alta |
| **Criterios de aceptación** | Copia automática diaria conservada 35 días, copia mensual conservada 12 meses y copia en una ubicación distinta (en el proyecto académico, mediante un script de respaldo y restauración); prueba de restauración cada 3 meses. |

## RNF-012 — Portabilidad y plataforma tecnológica

| Campo | Información |
|---|---|
| **Título de requerimiento** | Plataforma tecnológica |
| **Código de identificación** | RNF-012 |
| **Tipo de requerimiento** | No funcional — portabilidad |
| **Versión** | 0.4 |
| **Descripción** | El sistema deberá ser una aplicación web, utilizar PostgreSQL como motor de base de datos y ejecutarse en contenedores Docker, de modo que pueda desplegarse en cualquier proveedor de nube (por ejemplo AWS) y en distintas regiones según los países donde opere (ver RNF-014). En el proyecto académico se ejecuta de forma local con Docker Compose y se despliega, para la demostración, en una plataforma gratuita (Render). |
| **Prioridad** | Alta |
| **Criterios de aceptación** | El sistema opera correctamente sobre PostgreSQL y en contenedores Docker; en el proyecto académico, con un solo comando de Docker Compose en local y desplegado en la plataforma gratuita elegida. |

## RNF-013 — Funcionamiento sin conexión

| Campo | Información |
|---|---|
| **Título de requerimiento** | Disponibilidad sin conexión a internet |
| **Código de identificación** | RNF-013 |
| **Tipo de requerimiento** | No funcional — disponibilidad |
| **Versión** | 0.1 |
| **Descripción** | El sistema requiere conexión a internet para operar; sin conexión, solo será posible la visualización de productos, no así compras, ventas ni ajustes. |
| **Prioridad** | Media |
| **Criterios de aceptación** | Sin conexión, el sistema permite consultar el catálogo pero bloquea cualquier operación transaccional. |

## RNF-014 — Protección de datos personales

| Campo | Información |
|---|---|
| **Título de requerimiento** | Tratamiento de datos personales por país |
| **Código de identificación** | RNF-014 |
| **Tipo de requerimiento** | No funcional — seguridad / cumplimiento |
| **Versión** | 0.2 |
| **Descripción** | El sistema deberá registrar la autorización o base legal para tratar los datos personales del cliente, permitir consultarlos, actualizarlos y eliminarlos a solicitud, y aplicar la norma de protección de datos del país del cliente y del país de operación (p. ej. Colombia: Ley 1581 de 2012; Unión Europea: RGPD). Cuando la norma exija residencia de datos, el despliegue se realiza en una región de infraestructura que la cumpla. Cuando se compartan datos del cliente con transportadoras o agentes de aduanas de otros países, se cumplen las reglas de transferencia internacional de datos aplicables (ver la sección 7.2). |
| **Prioridad** | Alta |
| **Criterios de aceptación** | No se guarda un cliente sin autorización registrada; una solicitud de eliminación se atiende sin afectar los datos fiscales de las facturas; la norma aplicable se selecciona por configuración de país; los datos compartidos con terceros en otros países quedan registrados. |

## RNF-015 — Internacionalización y localización

| Campo | Información |
|---|---|
| **Título de requerimiento** | Soporte internacional del sistema |
| **Código de identificación** | RNF-015 |
| **Tipo de requerimiento** | No funcional — portabilidad / usabilidad |
| **Versión** | 0.1 |
| **Descripción** | Los textos de interfaz, mensajes, documentos y notificaciones se almacenan fuera del código como recursos traducibles. El sistema usa codificación Unicode (UTF-8), guarda las fechas en UTC y las muestra en la zona horaria configurada, guarda los importes con precisión decimal exacta y usa códigos estándar: ISO 3166-1 para países, ISO 4217 para monedas y etiquetas BCP 47 para idiomas. |
| **Prioridad** | Alta |
| **Criterios de aceptación** | Agregar un idioma, país o moneda se hace solo por configuración y carga de traducciones, sin modificar el código ni volver a desplegar; se verifica con al menos dos idiomas y dos monedas con distinto número de decimales. |

## RNF-016 — Parametrización

| Campo | Información |
|---|---|
| **Título de requerimiento** | Configuración sin cambios de código |
| **Código de identificación** | RNF-016 |
| **Tipo de requerimiento** | No funcional — mantenibilidad / adaptabilidad |
| **Versión** | 0.1 |
| **Descripción** | Los parámetros del sistema se guardan en un almacén de configuración con valor por defecto, ámbito y vigencia, se validan por rango y se aplican sin reiniciar el sistema. Cada cambio se versiona y audita (RF-020). |
| **Prioridad** | Alta |
| **Criterios de aceptación** | Un cambio de parámetro se aplica en un máximo de 5 minutos sin interrumpir el servicio; el sistema rechaza valores inválidos; se puede consultar el historial de cambios de cada parámetro. |

## RNF-017 — Operación en varios países

| Campo | Información |
|---|---|
| **Título de requerimiento** | Operación y ventas en varios países |
| **Código de identificación** | RNF-017 |
| **Tipo de requerimiento** | No funcional — seguridad / portabilidad |
| **Versión** | 0.3 |
| **Descripción** | El sistema deberá permitir que una sola tienda venda en línea a clientes de varios países y compre a proveedores de varios países, con una sola base de datos. Cada venta, compra, cliente, envío y documento fiscal se asocia a su país, y los permisos de un usuario pueden limitarse a uno o varios países. |
| **Prioridad** | Alta |
| **Criterios de aceptación** | Todo registro de venta, compra, cliente, envío y documento fiscal conserva su país; los reportes se pueden filtrar por país y consolidar en la moneda base; un usuario con permisos limitados a un país no puede consultar ni modificar datos de los demás países. |

---

# 7. Decisiones de cierre, consideraciones de operación real y alcance de la primera entrega

## 7.1 Decisiones tomadas por el equipo del proyecto

Estas decisiones cierran los puntos que estaban abiertos. Son los valores por defecto del sistema; todas son parametrizables y pueden revisarse sin cambiar el diseño.

| # | Tema | Decisión | Dónde se aplica |
|---|---|---|---|
| 1 | Aranceles e impuestos de importación | Por defecto los paga el cliente al recibir (DAP), con estimación visible antes de pagar; el negocio puede asumirlos (DDP) por país mediante configuración. | RF-025, RN-029 |
| 2 | País de origen y destinos iniciales | Origen: Colombia. Destinos iniciales: Estados Unidos, México, Perú, Ecuador y Chile, habilitados por fases después de Colombia. | RF-021 |
| 3 | Monedas | Moneda base: COP. Cobro internacional por defecto en USD. PEN, CLP y MXN como monedas de visualización y de cobro cuando la pasarela del país lo permita. | RF-022 |
| 4 | Idiomas | Español (por defecto) e inglés. | RF-023 |
| 5 | Tipos de cambio | Servicio externo con actualización diaria; carga manual de contingencia; margen configurable por el administrador. | RF-022 |
| 6 | Transportadoras | Un operador express internacional para ventas al exterior (candidatos a evaluar: DHL Express, FedEx, UPS) y un operador nacional (candidatos: Servientrega, Coordinadora, Interrapidísimo), mediante adaptadores. Se elige por API disponible, cobertura, costo y capacidad de despacho aduanero. | RF-024 |
| 7 | Aduanas y cálculo de aranceles | El operador express despacha los envíos pequeños; un agente de aduanas atiende los de mayor valor. El cálculo usa tablas propias parametrizadas (código arancelario por país) en la primera fase, con adaptador para un servicio externo en una fase posterior. | RF-025 |
| 8 | Productos restringidos o prohibidos | Tabla mantenida por el administrador (país por categoría, código arancelario o clasificación de edad), basada en la norma oficial del país y las condiciones de la transportadora; revisión cada 6 meses. | RF-025 |
| 9 | Pagos | Pasarela principal en Colombia con PSE, tarjetas, Nequi y Daviplata (candidatas: Wompi, PayU, ePayco; si ninguna cubre todos los medios, se agrega una segunda por adaptador). Cobro internacional con una pasarela que acepte tarjetas internacionales en USD (candidatas: PayU, PayPal; verificar disponibilidad para un comercio en Colombia). | RNF-008 |
| 10 | Facturación electrónica | El documento fiscal es el del país de origen, incluida la factura electrónica de exportación en ventas al exterior. Proveedor tecnológico habilitado por la DIAN (candidatos: Siigo, Alegra), elegido por soporte de exportación y calidad de API. Solo se agrega un adaptador de otro país si el negocio llega a tener obligaciones fiscales allí (por ejemplo, DDP con registro en el destino). | RF-021, RNF-008 |
| 11 | Proveedores oficiales iniciales | Distribuidores oficiales de las marcas que comercializa la tienda (Sony PlayStation, Microsoft Xbox, Nintendo, Sega y otras). Países de compra iniciales: Colombia y Estados Unidos. El administrador carga los proveedores al configurar el sistema. | RF-009, RF-026 |
| 12 | Anticipos de preventas y reservas | Si cancela el negocio o no entrega, devolución del 100%. Si cancela el cliente dentro del plazo de retracto del país, devolución del 100%; fuera de ese plazo, se retiene hasta el 10% y se devuelve el resto. | RF-019 |
| 13 | Cifras de carga | 10.000 usuarios registrados, 500 concurrentes en picos y 5-6 movimientos de inventario por día, con capacidad de crecer 10 veces en movimientos sin cambiar la arquitectura. | RNF-002, RNF-009 |
| 14 | Resto de reglas | Atributos de tarjetas, accesorios, retro, edición especial y logísticos; matriz de roles; escala de precios de usados; crédito y cuotas; promociones; reservas; recompra; metas de rendimiento y respaldos: decididos como se describen en los requisitos y reglas correspondientes. | RF-001, RF-006, RF-015 a RF-019, RN-013, RN-021 a RN-032, RNF-002, RNF-011 |

## 7.2 Consideraciones para una puesta en operación real (fuera del alcance de este proyecto académico)

Este es un proyecto académico: estas validaciones no se realizan ni son necesarias para entregarlo. Se documentan para mostrar que el diseño las contempla y qué haría falta si el sistema se llevara a operación real:

1. **Revisión aduanera y tributaria** por un agente de aduanas y un contador: tratamiento de las exportaciones desde el país de origen, aranceles e impuestos de importación en cada país de destino, y obligaciones tributarias si se habilita DDP en algún país.
2. **Revisión legal** de la protección de datos personales (país de origen y países de destino), los derechos del consumidor en ventas en línea (retracto y garantía) y los términos de preventas y anticipos.
3. **Selección, contratación y pruebas de integración** con la pasarela de pagos, el proveedor de facturación y las transportadoras. En el proyecto académico se documentan los criterios de selección (RNF-008) y las integraciones se simulan con adaptadores de prueba. La lista de candidatos puede cambiar según disponibilidad, costo y condiciones.
4. **Despliegue en la nube** (por ejemplo AWS): el sistema se entrega en contenedores Docker, por lo que puede llevarse a la nube sin cambios de diseño; en el proyecto académico se ejecuta de forma local con Docker Compose y se despliega, para la demostración, en una plataforma gratuita (Render).

## 7.3 Alcance de la primera entrega (corte vertical)

El proyecto académico se entrega funcionando. Para llegar a una versión que se pueda demostrar, la primera entrega incluye solo lo siguiente; el resto de los requisitos queda documentado como fase 2.

| Incluye | Detalle |
|---|---|
| Usuarios y roles | RF-006: inicio de sesión y permisos por rol según la matriz; sin autenticación multifactor. |
| Catálogo | RF-001 y RF-023: productos con atributos por tipo y nombres en español e inglés; sin atributos logísticos. |
| Proveedores y clientes | RF-009 y RF-015: registro y consulta básicos (el cliente con su país). |
| Inventario | RF-002 a RF-005 y RF-007: consulta, entradas, salidas (ventas, pérdidas, daños, robos y defectos), ajustes con aprobación del administrador e historial con auditoría; sin traslados ni devoluciones. |
| Parametrización internacional | RF-020 a RF-022: países (Colombia y Estados Unidos), monedas (COP y USD), tipos de cambio manuales, impuestos por país y parámetros con ámbito y vigencia. |
| Venta básica | RF-010: venta con moneda, tipo de cambio e impuesto del país; sin crédito, promociones ni facturación electrónica real (recibo simulado). |
| Reportes | RF-008: existencias y movimientos, exportables a CSV. |

**Fase 2 (solo documentada):** RF-011 a RF-014, RF-016 a RF-019, RF-024 a RF-026, autenticación multifactor, facturación electrónica real y el canal en línea para clientes.

### Tecnología y entorno de la primera entrega

- **Front:** React con Material UI. **Back:** Java con Spring Boot. **Base de datos:** PostgreSQL.
- **Parametrizable y reproducible:** el modelo de datos se describe en un JSON y scripts de Python generan de él el SQL de creación y las migraciones, y ejecutan todo el proceso de creación de la base de datos. Los archivos generados se pueden corregir a mano.
- **Calidad:** GitHub Actions como entorno de integración continua que ejecuta las pruebas y bloquea los cambios con errores; los resultados (logs) de las pruebas se guardan.
- **Ejecución:** local con Docker Compose; demostración desplegada en una plataforma gratuita (Render): back en contenedor, front como sitio estático y PostgreSQL gestionado.
- **Limitaciones de las plataformas gratuitas** (verificar sus condiciones vigentes): los servicios se suspenden tras un periodo de inactividad y tardan en reactivarse, y la base de datos gratuita caduca. Por eso la base de datos se puede recrear con los scripts de Python y se mantiene un script de respaldo y restauración.
- **Integraciones externas:** simuladas con adaptadores de prueba.

---

# 8. Decisiones de modelado

Estas son las decisiones de modelado de datos y su estado antes de diseñar tablas, claves y relaciones:

- **Producto vs. unidad física:** confirmado que se manejan números de serie individuales; los atributos por tipo de producto quedaron definidos en RF-001.
- **Catálogo vs. inventario:** resuelto conceptualmente — el catálogo describe el producto (código interno, categoría/subcategoría) y el inventario describe su existencia por bodega.
- **Ubicación:** resuelto — una sola tienda física con bodegas internas por categoría/subcategoría; no hay múltiples sucursales. Las ventas a otros países se hacen en línea desde esa tienda.
- **Movimiento vs. saldo:** decisión técnica de diseño; se resuelve en el diseño del modelo de datos (estrategia de cálculo/almacenamiento del saldo).
- **Ventas:** resuelto — forman parte del sistema; una venta confirmada oficialmente genera automáticamente un movimiento de salida.
- **Compras:** resuelto — se gestionan órdenes de compra con proveedores oficiales, incluyendo recepción parcial e importaciones.
- **Devoluciones:** resuelto — son movimientos sujetos a inspección previa (clientes) y también existen devoluciones a proveedores.
- **Usuarios:** resuelto — roles y permisos en la matriz de RF-006; un usuario puede tener varios roles.
- **Auditoría:** resuelto conceptualmente — se audita el 100% de las operaciones; retención mínima por defecto de 10 años antes de poder eliminar un registro.
- **Eliminación:** resuelto — los productos solo se desactivan, nunca se eliminan; el histórico de movimientos se depura tras 8 años (valor por defecto); los registros de auditoría no se eliminan antes de 10 años (valor por defecto).
- **Estados:** resuelto — estados de venta, pago, envío, compra, devolución y reserva definidos en las reglas complementarias de la sección 4.
- **País:** resuelto — una sola tienda opera en varios países; toda venta, compra, cliente, envío y documento fiscal registra su país.
- **Dinero y moneda:** resuelto conceptualmente — importes con precisión decimal exacta; cada transacción guarda moneda, tipo de cambio y equivalente en moneda base.
- **Parámetros:** resuelto conceptualmente — valor por defecto, ámbito (todo el negocio o un país) y vigencia, con auditoría de cambios.
- **Comercio exterior:** resuelto conceptualmente — el producto físico lleva código arancelario, peso, dimensiones y país de origen; el envío tiene estados propios; el costo de adquisición de lo importado incluye sus costos de importación (costo puesto en bodega).

---

# 9. Priorización inicial

La prioridad refleja el alcance confirmado por el cliente y las decisiones del proyecto. Lo que se construye primero está en la sección 7.3.

| Prioridad | Requisitos |
|---|---|
| Alta | RF-001 a RF-015, RF-020 a RF-026, RNF-001, RNF-002, RNF-003, RNF-004, RNF-005, RNF-007, RNF-008, RNF-010, RNF-011, RNF-012, RNF-014, RNF-015, RNF-016, RNF-017 |
| Media | RF-016 a RF-019, RNF-006, RNF-009, RNF-013 |
| Primera entrega (corte vertical) | RF-001 a RF-010 (con las limitaciones de la sección 7.3), RF-015, RF-020 a RF-023, RNF-003 (sin autenticación multifactor), RNF-004, RNF-006, RNF-010, RNF-012, RNF-015, RNF-016 |
| Fase 2 | RF-011 a RF-014, RF-016 a RF-019, RF-024 a RF-026 |
| Fuera de alcance académico | Los puntos de la sección 7.2 (solo para una operación real). |

---

# 10. Dependencias principales

```text
RF-001 Gestión de catálogo
        |
        +----> RF-002 Consulta de inventario
        |
        +----> RF-003 Entradas
        |
        +----> RF-004 Salidas/movimientos
                         |
                         +----> RF-005 Ajustes
                         |
                         +----> RF-007 Historial

RF-006 Usuarios/permisos
        |
        +----> Control de acceso de RF-001 a RF-008

RF-003 + RF-004 + RF-005
        |
        +----> RF-008 Reportes

RF-009 Proveedores ---- alimenta RF-003 (compras)
RF-010 Ventas --------- alimenta RF-004 (salidas) y RF-014 (comercio electrónico)
RF-011 Devoluciones --- depende de RF-010 y RF-009
RF-012 Serialización -- depende de RF-001 y alimenta RF-013 (garantías)
RF-013 Garantías ------ depende de RF-001 y RF-012
RF-014 Comercio electrónico ---- depende de RF-010 y RF-009
RF-015 Clientes ------------- alimenta RF-010 (ventas) y RF-011 (devoluciones)
RF-016 Recompra de usados --- depende de RF-001, RF-003 y RF-012
RF-017 Traslados ----------- depende de RF-002 y RF-004
RF-018 Alertas de stock ---- depende de RF-002
RF-019 Preventas y reservas - depende de RF-002 y RF-010

RF-020 Parametrización ----- alimenta a todos los requisitos (reglas, catálogos y umbrales)
RF-021 Configuración país -- depende de RF-020; alimenta RF-010, RF-011, RF-014, RF-015, RF-024 y RF-025
RF-022 Multimoneda -------- depende de RF-021; alimenta RF-003, RF-008, RF-009, RF-010 y RF-026
RF-023 Idiomas ------------ depende de RF-020; alimenta RF-001, RF-008 y RF-014

RF-024 Envíos ---------------- depende de RF-001, RF-010, RF-012 y RF-021; alimenta RF-025
RF-025 Aranceles y aduanas --- depende de RF-001, RF-010, RF-021 y RF-024; alimenta RF-010 (costo estimado) y RF-011
RF-026 Importaciones --------- depende de RF-009, RF-021 y RF-022; alimenta RF-003 (costo de adquisición)
```

---

# 11. Criterios de calidad de los requisitos

Siguiendo el enfoque de ingeniería de requisitos de ISO/IEC/IEEE 29148:2018, los requisitos deben revisarse para que sean suficientemente claros, precisos, verificables y trazables. La norma trata explícitamente la ingeniería, documentación, verificación, validación y gestión de requisitos.

Antes de aprobar cada requisito se debe comprobar:

- ¿Está expresado de manera clara y sin ambigüedad?
- ¿Representa una necesidad real de un actor o parte interesada?
- ¿Es verificable mediante una prueba, inspección o análisis?
- ¿Tiene un identificador único?
- ¿Tiene una fuente o responsable que permita rastrear su origen?
- ¿Tiene criterios de aceptación?
- ¿Tiene dependencias identificadas?
- ¿Es consistente con los demás requisitos?
- ¿Puede implementarse sin introducir una decisión de negocio que todavía no haya sido aprobada?
- ¿Puede modificarse y rastrearse posteriormente?
- ¿Evita fijar valores propios de un país o negocio (se expresan como parámetro con valor por defecto)?

---

# 12. Trazabilidad inicial

| Necesidad | Requisitos relacionados | Preguntas que afectan el diseño |
|---|---|---|
| Controlar productos | RF-001 | 6–16 |
| Conocer existencias | RF-002 | 17–29 |
| Registrar entradas | RF-003 | 30–37 |
| Registrar salidas | RF-004 | 22–29, 38–49 |
| Conciliar inventario | RF-005 | 23–29 |
| Controlar acceso | RF-006 | 56–65 |
| Auditar movimientos | RF-007, RNF-010 | 63–65, 74–84 |
| Generar reportes | RF-008 | 66–73 |
| Gestionar compras | RF-009 | 30–37 |
| Gestionar ventas | RF-010 | 38–49 |
| Gestionar devoluciones | RF-011 | 48–49 |
| Rastrear unidades | RF-012 | 9–12, 50–55 |
| Gestionar garantías y servicio | RF-013 | 50–55 |
| Comercio electrónico | RF-014 | 4, 84 |
| Gestionar clientes | RF-015, RNF-014 | — (requisito nuevo, decidido) |
| Recomprar usados | RF-016 | — (requisito nuevo, decidido) |
| Trasladar entre bodegas | RF-017 | — (requisito nuevo, decidido) |
| Alertar stock | RF-018 | — (requisito nuevo, decidido) |
| Preventas y reservas | RF-019 | — (requisito nuevo, decidido) |
| Parametrizar el sistema | RF-020, RNF-016 | — (requisito nuevo, decidido) |
| Operar en varios países | RF-021, RNF-014, RNF-017 | — (requisito nuevo, decidido) |
| Operar en varias monedas | RF-022 | — (requisito nuevo, decidido) |
| Operar en varios idiomas | RF-023, RNF-015 | — (requisito nuevo, decidido) |
| Enviar pedidos a otros países | RF-024 | — (requisito nuevo, decidido) |
| Cobrar aranceles y cumplir aduanas en ventas | RF-025 | — (requisito nuevo, decidido) |
| Importar mercancía de otros países | RF-026 | — (requisito nuevo, decidido) |

---

# 13. Estado del levantamiento

**Estado general:** Levantamiento cerrado. Todos los puntos abiertos quedaron resueltos con decisiones del equipo del proyecto (sección 7.1), y las decisiones que cambien por revisión del cliente se aplican por configuración.

**Listo para pasar al modelo de datos y al diseño.** El país debe registrarse desde el inicio en ventas, compras, clientes, envíos y documentos fiscales, y el producto físico debe llevar sus datos logísticos y arancelarios. El alcance de la primera entrega está definido en la sección 7.3. Las consideraciones de la sección 7.2 solo aplicarían si el sistema se llevara a operación real; no son necesarias para este proyecto académico.

---

# 14. Referencias normativas

- ISO/IEC/IEEE 29148:2018 — *Systems and software engineering — Life cycle processes — Requirements engineering*. ISO indica que esta edición fue publicada en noviembre de 2018 y fue revisada/confirmada en 2024; al momento de elaborar este documento continúa siendo la edición publicada de referencia, aunque existe una nueva versión DIS en desarrollo.
- ISO/IEC 25010:2023 — *Systems and software engineering — Systems and software Quality Requirements and Evaluation (SQuaRE) — Product quality model*. ISO indica que esta segunda edición fue publicada en noviembre de 2023 y reemplazó a ISO/IEC 25010:2011.

**Referencias técnicas de internacionalización y comercio exterior:** ISO 3166-1 (códigos de país), ISO 4217 (códigos de moneda), IETF BCP 47 (etiquetas de idioma), Sistema Armonizado de Designación y Codificación de Mercancías de la Organización Mundial de Aduanas (clasificación arancelaria) e Incoterms® de la Cámara de Comercio Internacional (condiciones de entrega como DAP y DDP).

**Nota:** este documento utiliza las normas como referencia metodológica. No reproduce el contenido protegido de las normas.