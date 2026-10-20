-- GENERADO — no editar; modifique el JSON del modelo o la plantilla.
CREATE TABLE IF NOT EXISTS public."categoria" (
    "id" UUID PRIMARY KEY,
    "codigo" TEXT NOT NULL,
    "nombre" TEXT NOT NULL,
    "padre_id" UUID,
    "activo" BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT "fk_categoria_padre_id" FOREIGN KEY ("padre_id") REFERENCES public."categoria" ("id") ON DELETE RESTRICT,
    CONSTRAINT "uq_categoria_codigo" UNIQUE ("codigo")
);

CREATE TABLE IF NOT EXISTS public."tipo_producto" (
    "id" UUID PRIMARY KEY,
    "codigo" TEXT NOT NULL,
    "nombre" TEXT NOT NULL,
    "activo" BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT "uq_tipo_producto_codigo" UNIQUE ("codigo")
);

CREATE TABLE IF NOT EXISTS public."atributo_definicion" (
    "id" UUID PRIMARY KEY,
    "tipo_producto_id" UUID NOT NULL,
    "nombre" TEXT NOT NULL,
    "tipo_dato" TEXT NOT NULL,
    "obligatorio" BOOLEAN NOT NULL DEFAULT FALSE,
    "opciones" JSONB,
    CONSTRAINT "fk_atributo_definicion_tipo_producto_id" FOREIGN KEY ("tipo_producto_id") REFERENCES public."tipo_producto" ("id") ON DELETE RESTRICT,
    CONSTRAINT "ck_atributo_definicion_tipo_dato_valores" CHECK ("tipo_dato" IN ('TEXTO', 'ENTERO', 'DECIMAL', 'BOOLEANO', 'JSON')),
    CONSTRAINT "uq_atributo_definicion_tipo_producto_id_nombre" UNIQUE ("tipo_producto_id", "nombre")
);

CREATE TABLE IF NOT EXISTS public."producto" (
    "id" UUID PRIMARY KEY,
    "codigo_interno" TEXT NOT NULL,
    "tipo_producto_id" UUID NOT NULL,
    "categoria_id" UUID NOT NULL,
    "subcategoria_id" UUID NOT NULL,
    "atributos" JSONB NOT NULL,
    "activo" BOOLEAN NOT NULL DEFAULT TRUE,
    "creado_en_utc" TIMESTAMPTZ NOT NULL,
    "actualizado_en_utc" TIMESTAMPTZ NOT NULL,
    CONSTRAINT "fk_producto_tipo_producto_id" FOREIGN KEY ("tipo_producto_id") REFERENCES public."tipo_producto" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_producto_categoria_id" FOREIGN KEY ("categoria_id") REFERENCES public."categoria" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_producto_subcategoria_id" FOREIGN KEY ("subcategoria_id") REFERENCES public."categoria" ("id") ON DELETE RESTRICT,
    CONSTRAINT "uq_producto_codigo_interno" UNIQUE ("codigo_interno")
);

CREATE TABLE IF NOT EXISTS public."bodega" (
    "id" UUID PRIMARY KEY,
    "codigo" TEXT NOT NULL,
    "nombre" TEXT NOT NULL,
    "categoria_id" UUID,
    "activo" BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT "fk_bodega_categoria_id" FOREIGN KEY ("categoria_id") REFERENCES public."categoria" ("id") ON DELETE RESTRICT,
    CONSTRAINT "uq_bodega_codigo" UNIQUE ("codigo")
);

CREATE TABLE IF NOT EXISTS public."saldo_inventario" (
    "id" UUID PRIMARY KEY,
    "producto_id" UUID NOT NULL,
    "bodega_id" UUID NOT NULL,
    "cantidad" INTEGER NOT NULL,
    "actualizado_en_utc" TIMESTAMPTZ NOT NULL,
    CONSTRAINT "fk_saldo_inventario_producto_id" FOREIGN KEY ("producto_id") REFERENCES public."producto" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_saldo_inventario_bodega_id" FOREIGN KEY ("bodega_id") REFERENCES public."bodega" ("id") ON DELETE RESTRICT,
    CONSTRAINT "ck_saldo_inventario_cantidad_min" CHECK ("cantidad" >= 0),
    CONSTRAINT "uq_saldo_inventario_producto_id_bodega_id" UNIQUE ("producto_id", "bodega_id")
);

CREATE TABLE IF NOT EXISTS public."moneda" (
    "id" UUID PRIMARY KEY,
    "codigo_iso" TEXT NOT NULL,
    "nombre" TEXT NOT NULL,
    "simbolo" TEXT NOT NULL,
    "decimales" INTEGER NOT NULL,
    "formato" TEXT NOT NULL,
    "regla_redondeo" TEXT NOT NULL,
    CONSTRAINT "ck_moneda_decimales_min" CHECK ("decimales" >= 0),
    CONSTRAINT "ck_moneda_decimales_max" CHECK ("decimales" <= 4),
    CONSTRAINT "uq_moneda_codigo_iso" UNIQUE ("codigo_iso")
);

CREATE TABLE IF NOT EXISTS public."precio_producto" (
    "id" UUID PRIMARY KEY,
    "producto_id" UUID NOT NULL,
    "moneda_id" UUID NOT NULL,
    "importe" NUMERIC(19,4) NOT NULL,
    "vigente_desde" DATE NOT NULL,
    "vigente_hasta" DATE,
    CONSTRAINT "fk_precio_producto_producto_id" FOREIGN KEY ("producto_id") REFERENCES public."producto" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_precio_producto_moneda_id" FOREIGN KEY ("moneda_id") REFERENCES public."moneda" ("id") ON DELETE RESTRICT,
    CONSTRAINT "ck_precio_producto_importe_min" CHECK ("importe" >= 0)
);

CREATE TABLE IF NOT EXISTS public."idioma" (
    "id" UUID PRIMARY KEY,
    "codigo_bcp47" TEXT NOT NULL,
    "nombre" TEXT NOT NULL,
    "habilitado" BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT "uq_idioma_codigo_bcp47" UNIQUE ("codigo_bcp47")
);

CREATE TABLE IF NOT EXISTS public."producto_traduccion" (
    "id" UUID PRIMARY KEY,
    "producto_id" UUID NOT NULL,
    "idioma_id" UUID NOT NULL,
    "nombre" TEXT NOT NULL,
    "descripcion" TEXT,
    CONSTRAINT "fk_producto_traduccion_producto_id" FOREIGN KEY ("producto_id") REFERENCES public."producto" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_producto_traduccion_idioma_id" FOREIGN KEY ("idioma_id") REFERENCES public."idioma" ("id") ON DELETE RESTRICT,
    CONSTRAINT "uq_producto_traduccion_producto_id_idioma_id" UNIQUE ("producto_id", "idioma_id")
);

CREATE TABLE IF NOT EXISTS public."pais" (
    "id" UUID PRIMARY KEY,
    "codigo_iso" TEXT NOT NULL,
    "nombre" TEXT NOT NULL,
    "moneda_local_id" UUID,
    "idioma_predeterminado_id" UUID,
    "zona_horaria" TEXT,
    "habilitado" BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT "fk_pais_moneda_local_id" FOREIGN KEY ("moneda_local_id") REFERENCES public."moneda" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_pais_idioma_predeterminado_id" FOREIGN KEY ("idioma_predeterminado_id") REFERENCES public."idioma" ("id") ON DELETE RESTRICT,
    CONSTRAINT "uq_pais_codigo_iso" UNIQUE ("codigo_iso")
);

CREATE TABLE IF NOT EXISTS public."impuesto" (
    "id" UUID PRIMARY KEY,
    "pais_id" UUID NOT NULL,
    "categoria_id" UUID,
    "nombre" TEXT NOT NULL,
    "tasa" NUMERIC(9,6) NOT NULL,
    "incluido_en_precio" BOOLEAN NOT NULL,
    "vigente_desde" DATE NOT NULL,
    "vigente_hasta" DATE,
    CONSTRAINT "fk_impuesto_pais_id" FOREIGN KEY ("pais_id") REFERENCES public."pais" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_impuesto_categoria_id" FOREIGN KEY ("categoria_id") REFERENCES public."categoria" ("id") ON DELETE RESTRICT,
    CONSTRAINT "ck_impuesto_tasa_min" CHECK ("tasa" >= 0)
);

CREATE TABLE IF NOT EXISTS public."usuario" (
    "id" UUID PRIMARY KEY,
    "correo" TEXT NOT NULL,
    "nombre" TEXT NOT NULL,
    "hash_contrasena" TEXT NOT NULL,
    "idioma_id" UUID,
    "intentos_fallidos" INTEGER NOT NULL DEFAULT 0,
    "bloqueado_hasta_utc" TIMESTAMPTZ,
    "activo" BOOLEAN NOT NULL DEFAULT TRUE,
    "creado_en_utc" TIMESTAMPTZ NOT NULL,
    CONSTRAINT "fk_usuario_idioma_id" FOREIGN KEY ("idioma_id") REFERENCES public."idioma" ("id") ON DELETE RESTRICT,
    CONSTRAINT "ck_usuario_intentos_fallidos_min" CHECK ("intentos_fallidos" >= 0),
    CONSTRAINT "uq_usuario_correo" UNIQUE ("correo")
);

CREATE TABLE IF NOT EXISTS public."movimiento_inventario" (
    "id" UUID PRIMARY KEY,
    "producto_id" UUID NOT NULL,
    "bodega_id" UUID NOT NULL,
    "tipo" TEXT NOT NULL,
    "cantidad" INTEGER NOT NULL,
    "referencia_tipo" TEXT NOT NULL,
    "referencia_id" UUID NOT NULL,
    "usuario_id" UUID NOT NULL,
    "fecha_utc" TIMESTAMPTZ NOT NULL,
    "observacion" TEXT,
    CONSTRAINT "fk_movimiento_inventario_producto_id" FOREIGN KEY ("producto_id") REFERENCES public."producto" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_movimiento_inventario_bodega_id" FOREIGN KEY ("bodega_id") REFERENCES public."bodega" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_movimiento_inventario_usuario_id" FOREIGN KEY ("usuario_id") REFERENCES public."usuario" ("id") ON DELETE RESTRICT,
    CONSTRAINT "ck_movimiento_inventario_tipo_valores" CHECK ("tipo" IN ('ENTRADA', 'SALIDA_VENTA', 'SALIDA_PERDIDA', 'SALIDA_DANO', 'SALIDA_ROBO', 'SALIDA_DEFECTO', 'AJUSTE')),
    CONSTRAINT "ck_movimiento_inventario_cantidad_min" CHECK ("cantidad" >= 1)
);

CREATE TABLE IF NOT EXISTS public."ajuste_inventario" (
    "id" UUID PRIMARY KEY,
    "producto_id" UUID NOT NULL,
    "bodega_id" UUID NOT NULL,
    "tipo" TEXT NOT NULL,
    "cantidad_sistema" INTEGER NOT NULL,
    "cantidad_contada" INTEGER NOT NULL,
    "diferencia" INTEGER NOT NULL,
    "motivo" TEXT NOT NULL,
    "estado" TEXT NOT NULL,
    "solicitado_por_id" UUID NOT NULL,
    "aprobado_por_id" UUID,
    "solicitado_en_utc" TIMESTAMPTZ NOT NULL,
    "aprobado_en_utc" TIMESTAMPTZ,
    "aplicado_en_utc" TIMESTAMPTZ,
    CONSTRAINT "fk_ajuste_inventario_producto_id" FOREIGN KEY ("producto_id") REFERENCES public."producto" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_ajuste_inventario_bodega_id" FOREIGN KEY ("bodega_id") REFERENCES public."bodega" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_ajuste_inventario_solicitado_por_id" FOREIGN KEY ("solicitado_por_id") REFERENCES public."usuario" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_ajuste_inventario_aprobado_por_id" FOREIGN KEY ("aprobado_por_id") REFERENCES public."usuario" ("id") ON DELETE RESTRICT,
    CONSTRAINT "ck_ajuste_inventario_tipo_valores" CHECK ("tipo" IN ('OPERATIVO', 'TECNICO')),
    CONSTRAINT "ck_ajuste_inventario_cantidad_sistema_min" CHECK ("cantidad_sistema" >= 0),
    CONSTRAINT "ck_ajuste_inventario_cantidad_contada_min" CHECK ("cantidad_contada" >= 0),
    CONSTRAINT "ck_ajuste_inventario_estado_valores" CHECK ("estado" IN ('PENDIENTE_DE_APROBACION', 'APROBADO', 'APLICADO'))
);

CREATE TABLE IF NOT EXISTS public."parametro" (
    "id" UUID PRIMARY KEY,
    "clave" TEXT NOT NULL,
    "valor" JSONB NOT NULL,
    "tipo" TEXT NOT NULL,
    "minimo" NUMERIC(19,4),
    "maximo" NUMERIC(19,4),
    "valor_por_defecto" JSONB NOT NULL,
    "ambito" TEXT NOT NULL,
    "pais_id" UUID,
    "vigente_desde" DATE NOT NULL,
    "vigente_hasta" DATE,
    "actualizado_por_id" UUID NOT NULL,
    "actualizado_en_utc" TIMESTAMPTZ NOT NULL,
    CONSTRAINT "fk_parametro_pais_id" FOREIGN KEY ("pais_id") REFERENCES public."pais" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_parametro_actualizado_por_id" FOREIGN KEY ("actualizado_por_id") REFERENCES public."usuario" ("id") ON DELETE RESTRICT,
    CONSTRAINT "ck_parametro_tipo_valores" CHECK ("tipo" IN ('TEXTO', 'ENTERO', 'DECIMAL', 'BOOLEANO', 'JSON')),
    CONSTRAINT "ck_parametro_ambito_valores" CHECK ("ambito" IN ('NEGOCIO', 'PAIS'))
);

CREATE TABLE IF NOT EXISTS public."tipo_cambio" (
    "id" UUID PRIMARY KEY,
    "moneda_origen_id" UUID NOT NULL,
    "moneda_destino_id" UUID NOT NULL,
    "tasa" NUMERIC(19,8) NOT NULL,
    "vigente_desde_utc" TIMESTAMPTZ NOT NULL,
    "origen" TEXT NOT NULL,
    "actualizado_por_id" UUID NOT NULL,
    CONSTRAINT "fk_tipo_cambio_moneda_origen_id" FOREIGN KEY ("moneda_origen_id") REFERENCES public."moneda" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_tipo_cambio_moneda_destino_id" FOREIGN KEY ("moneda_destino_id") REFERENCES public."moneda" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_tipo_cambio_actualizado_por_id" FOREIGN KEY ("actualizado_por_id") REFERENCES public."usuario" ("id") ON DELETE RESTRICT,
    CONSTRAINT "ck_tipo_cambio_tasa_min" CHECK ("tasa" >= 0),
    CONSTRAINT "ck_tipo_cambio_origen_valores" CHECK ("origen" IN ('MANUAL', 'SERVICIO'))
);

CREATE TABLE IF NOT EXISTS public."rol" (
    "id" UUID PRIMARY KEY,
    "codigo" TEXT NOT NULL,
    "nombre" TEXT NOT NULL,
    "activo" BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT "uq_rol_codigo" UNIQUE ("codigo")
);

CREATE TABLE IF NOT EXISTS public."usuario_rol" (
    "id" UUID PRIMARY KEY,
    "usuario_id" UUID NOT NULL,
    "rol_id" UUID NOT NULL,
    CONSTRAINT "fk_usuario_rol_usuario_id" FOREIGN KEY ("usuario_id") REFERENCES public."usuario" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_usuario_rol_rol_id" FOREIGN KEY ("rol_id") REFERENCES public."rol" ("id") ON DELETE RESTRICT,
    CONSTRAINT "uq_usuario_rol_usuario_id_rol_id" UNIQUE ("usuario_id", "rol_id")
);

CREATE TABLE IF NOT EXISTS public."permiso" (
    "id" UUID PRIMARY KEY,
    "codigo" TEXT NOT NULL,
    "descripcion" TEXT NOT NULL,
    CONSTRAINT "uq_permiso_codigo" UNIQUE ("codigo")
);

CREATE TABLE IF NOT EXISTS public."rol_permiso" (
    "id" UUID PRIMARY KEY,
    "rol_id" UUID NOT NULL,
    "permiso_id" UUID NOT NULL,
    CONSTRAINT "fk_rol_permiso_rol_id" FOREIGN KEY ("rol_id") REFERENCES public."rol" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_rol_permiso_permiso_id" FOREIGN KEY ("permiso_id") REFERENCES public."permiso" ("id") ON DELETE RESTRICT,
    CONSTRAINT "uq_rol_permiso_rol_id_permiso_id" UNIQUE ("rol_id", "permiso_id")
);

CREATE TABLE IF NOT EXISTS public."usuario_pais" (
    "id" UUID PRIMARY KEY,
    "usuario_id" UUID NOT NULL,
    "pais_id" UUID NOT NULL,
    CONSTRAINT "fk_usuario_pais_usuario_id" FOREIGN KEY ("usuario_id") REFERENCES public."usuario" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_usuario_pais_pais_id" FOREIGN KEY ("pais_id") REFERENCES public."pais" ("id") ON DELETE RESTRICT,
    CONSTRAINT "uq_usuario_pais_usuario_id_pais_id" UNIQUE ("usuario_id", "pais_id")
);

CREATE TABLE IF NOT EXISTS public."proveedor" (
    "id" UUID PRIMARY KEY,
    "razon_social" TEXT NOT NULL,
    "pais_id" UUID NOT NULL,
    "identificacion_tributaria" TEXT,
    "persona_contacto" TEXT,
    "correo" TEXT,
    "telefono" TEXT,
    "moneda_compra_id" UUID NOT NULL,
    "condiciones_pago" TEXT,
    "activo" BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT "fk_proveedor_pais_id" FOREIGN KEY ("pais_id") REFERENCES public."pais" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_proveedor_moneda_compra_id" FOREIGN KEY ("moneda_compra_id") REFERENCES public."moneda" ("id") ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS public."orden_compra" (
    "id" UUID PRIMARY KEY,
    "numero" TEXT NOT NULL,
    "proveedor_id" UUID NOT NULL,
    "pais_id" UUID NOT NULL,
    "moneda_id" UUID NOT NULL,
    "tipo_cambio" NUMERIC(19,8),
    "estado" TEXT NOT NULL,
    "creada_por_id" UUID NOT NULL,
    "creada_en_utc" TIMESTAMPTZ NOT NULL,
    CONSTRAINT "fk_orden_compra_proveedor_id" FOREIGN KEY ("proveedor_id") REFERENCES public."proveedor" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_orden_compra_pais_id" FOREIGN KEY ("pais_id") REFERENCES public."pais" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_orden_compra_moneda_id" FOREIGN KEY ("moneda_id") REFERENCES public."moneda" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_orden_compra_creada_por_id" FOREIGN KEY ("creada_por_id") REFERENCES public."usuario" ("id") ON DELETE RESTRICT,
    CONSTRAINT "ck_orden_compra_tipo_cambio_min" CHECK ("tipo_cambio" >= 0.00000001),
    CONSTRAINT "ck_orden_compra_estado_valores" CHECK ("estado" IN ('BORRADOR', 'ENVIADA_AL_PROVEEDOR', 'RECIBIDA_PARCIAL', 'RECIBIDA_COMPLETA', 'CANCELADA')),
    CONSTRAINT "uq_orden_compra_numero" UNIQUE ("numero")
);

CREATE TABLE IF NOT EXISTS public."orden_compra_linea" (
    "id" UUID PRIMARY KEY,
    "orden_compra_id" UUID NOT NULL,
    "producto_id" UUID NOT NULL,
    "cantidad_pedida" INTEGER NOT NULL,
    "costo_unitario" NUMERIC(19,4) NOT NULL,
    CONSTRAINT "fk_orden_compra_linea_orden_compra_id" FOREIGN KEY ("orden_compra_id") REFERENCES public."orden_compra" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_orden_compra_linea_producto_id" FOREIGN KEY ("producto_id") REFERENCES public."producto" ("id") ON DELETE RESTRICT,
    CONSTRAINT "ck_orden_compra_linea_cantidad_pedida_min" CHECK ("cantidad_pedida" >= 1),
    CONSTRAINT "ck_orden_compra_linea_costo_unitario_min" CHECK ("costo_unitario" >= 0),
    CONSTRAINT "uq_orden_compra_linea_orden_compra_id_producto_id" UNIQUE ("orden_compra_id", "producto_id")
);

CREATE TABLE IF NOT EXISTS public."entrada_inventario" (
    "id" UUID PRIMARY KEY,
    "orden_compra_id" UUID NOT NULL,
    "bodega_id" UUID NOT NULL,
    "numero_documento" TEXT NOT NULL,
    "tipo_cambio_aplicado" NUMERIC(19,8) NOT NULL,
    "recibida_por_id" UUID NOT NULL,
    "recibida_en_utc" TIMESTAMPTZ NOT NULL,
    CONSTRAINT "fk_entrada_inventario_orden_compra_id" FOREIGN KEY ("orden_compra_id") REFERENCES public."orden_compra" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_entrada_inventario_bodega_id" FOREIGN KEY ("bodega_id") REFERENCES public."bodega" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_entrada_inventario_recibida_por_id" FOREIGN KEY ("recibida_por_id") REFERENCES public."usuario" ("id") ON DELETE RESTRICT,
    CONSTRAINT "ck_entrada_inventario_tipo_cambio_aplicado_min" CHECK ("tipo_cambio_aplicado" >= 0.00000001)
);

CREATE TABLE IF NOT EXISTS public."entrada_inventario_linea" (
    "id" UUID PRIMARY KEY,
    "entrada_inventario_id" UUID NOT NULL,
    "orden_compra_linea_id" UUID NOT NULL,
    "producto_id" UUID NOT NULL,
    "cantidad" INTEGER NOT NULL,
    "costo_unitario" NUMERIC(19,4) NOT NULL,
    "costo_unitario_moneda_base" NUMERIC(19,4) NOT NULL,
    CONSTRAINT "fk_entrada_inventario_linea_entrada_inventario_id" FOREIGN KEY ("entrada_inventario_id") REFERENCES public."entrada_inventario" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_entrada_inventario_linea_orden_compra_linea_id" FOREIGN KEY ("orden_compra_linea_id") REFERENCES public."orden_compra_linea" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_entrada_inventario_linea_producto_id" FOREIGN KEY ("producto_id") REFERENCES public."producto" ("id") ON DELETE RESTRICT,
    CONSTRAINT "ck_entrada_inventario_linea_cantidad_min" CHECK ("cantidad" >= 1),
    CONSTRAINT "ck_entrada_inventario_linea_costo_unitario_min" CHECK ("costo_unitario" >= 0),
    CONSTRAINT "ck_entrada_inventario_linea_costo_unitario_moneda_base_min" CHECK ("costo_unitario_moneda_base" >= 0)
);

CREATE TABLE IF NOT EXISTS public."producto_proveedor" (
    "id" UUID PRIMARY KEY,
    "producto_id" UUID NOT NULL,
    "proveedor_id" UUID NOT NULL,
    CONSTRAINT "fk_producto_proveedor_producto_id" FOREIGN KEY ("producto_id") REFERENCES public."producto" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_producto_proveedor_proveedor_id" FOREIGN KEY ("proveedor_id") REFERENCES public."proveedor" ("id") ON DELETE RESTRICT,
    CONSTRAINT "uq_producto_proveedor_producto_id_proveedor_id" UNIQUE ("producto_id", "proveedor_id")
);

CREATE TABLE IF NOT EXISTS public."cliente" (
    "id" UUID PRIMARY KEY,
    "pais_id" UUID NOT NULL,
    "clasificacion" TEXT NOT NULL,
    "nombre" TEXT NOT NULL,
    "apellidos" TEXT,
    "tipo_documento" TEXT,
    "documento" TEXT,
    "codigo_postal" TEXT,
    "direccion" TEXT,
    "telefono" TEXT,
    "correo" TEXT NOT NULL,
    "base_legal" TEXT NOT NULL,
    "consentimiento_registrado" BOOLEAN NOT NULL,
    "consentimiento_en_utc" TIMESTAMPTZ NOT NULL,
    "ultimo_contacto_en_utc" TIMESTAMPTZ NOT NULL,
    "activo" BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT "fk_cliente_pais_id" FOREIGN KEY ("pais_id") REFERENCES public."pais" ("id") ON DELETE RESTRICT,
    CONSTRAINT "ck_cliente_clasificacion_valores" CHECK ("clasificacion" IN ('INFRECUENTE', 'FRECUENTE'))
);

CREATE TABLE IF NOT EXISTS public."auditoria" (
    "id" UUID PRIMARY KEY,
    "usuario_id" UUID,
    "pais_id" UUID,
    "accion" TEXT NOT NULL,
    "entidad" TEXT NOT NULL,
    "registro_id" UUID,
    "datos_anteriores" JSONB,
    "datos_nuevos" JSONB,
    "fecha_utc" TIMESTAMPTZ NOT NULL,
    "contexto" JSONB,
    CONSTRAINT "fk_auditoria_usuario_id" FOREIGN KEY ("usuario_id") REFERENCES public."usuario" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_auditoria_pais_id" FOREIGN KEY ("pais_id") REFERENCES public."pais" ("id") ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS public."migracion_historial" (
    "id" UUID PRIMARY KEY,
    "version" TEXT NOT NULL,
    "checksum" TEXT NOT NULL,
    "aplicada_en_utc" TIMESTAMPTZ NOT NULL,
    CONSTRAINT "uq_migracion_historial_version" UNIQUE ("version")
);

CREATE TABLE IF NOT EXISTS public."venta" (
    "id" UUID PRIMARY KEY,
    "numero" TEXT NOT NULL,
    "cliente_id" UUID,
    "pais_id" UUID NOT NULL,
    "moneda_id" UUID NOT NULL,
    "tipo_cambio_aplicado" NUMERIC(19,8) NOT NULL,
    "subtotal" NUMERIC(19,4) NOT NULL,
    "total_impuestos" NUMERIC(19,4) NOT NULL,
    "total" NUMERIC(19,4) NOT NULL,
    "total_moneda_base" NUMERIC(19,4) NOT NULL,
    "estado" TEXT NOT NULL,
    "estado_pago" TEXT NOT NULL,
    "medio_pago" TEXT,
    "registrada_por_id" UUID NOT NULL,
    "creada_en_utc" TIMESTAMPTZ NOT NULL,
    "pagada_en_utc" TIMESTAMPTZ,
    "confirmada_en_utc" TIMESTAMPTZ,
    "anulada_en_utc" TIMESTAMPTZ,
    CONSTRAINT "fk_venta_cliente_id" FOREIGN KEY ("cliente_id") REFERENCES public."cliente" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_venta_pais_id" FOREIGN KEY ("pais_id") REFERENCES public."pais" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_venta_moneda_id" FOREIGN KEY ("moneda_id") REFERENCES public."moneda" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_venta_registrada_por_id" FOREIGN KEY ("registrada_por_id") REFERENCES public."usuario" ("id") ON DELETE RESTRICT,
    CONSTRAINT "ck_venta_tipo_cambio_aplicado_min" CHECK ("tipo_cambio_aplicado" >= 0),
    CONSTRAINT "ck_venta_subtotal_min" CHECK ("subtotal" >= 0),
    CONSTRAINT "ck_venta_total_impuestos_min" CHECK ("total_impuestos" >= 0),
    CONSTRAINT "ck_venta_total_min" CHECK ("total" >= 0),
    CONSTRAINT "ck_venta_estado_valores" CHECK ("estado" IN ('PENDIENTE_DE_PAGO', 'CONFIRMADA', 'ANULADA')),
    CONSTRAINT "ck_venta_estado_pago_valores" CHECK ("estado_pago" IN ('SIN_PAGO', 'PAGADO')),
    CONSTRAINT "uq_venta_numero" UNIQUE ("numero")
);

CREATE TABLE IF NOT EXISTS public."venta_linea" (
    "id" UUID PRIMARY KEY,
    "venta_id" UUID NOT NULL,
    "producto_id" UUID NOT NULL,
    "bodega_id" UUID NOT NULL,
    "cantidad" INTEGER NOT NULL,
    "precio_unitario" NUMERIC(19,4) NOT NULL,
    "impuesto_id" UUID NOT NULL,
    "nombre_impuesto_aplicado" TEXT NOT NULL,
    "tasa_impuesto_aplicada" NUMERIC(9,6) NOT NULL,
    "importe_impuesto" NUMERIC(19,4) NOT NULL,
    "subtotal_linea" NUMERIC(19,4) NOT NULL,
    "total_linea" NUMERIC(19,4) NOT NULL,
    CONSTRAINT "fk_venta_linea_venta_id" FOREIGN KEY ("venta_id") REFERENCES public."venta" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_venta_linea_producto_id" FOREIGN KEY ("producto_id") REFERENCES public."producto" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_venta_linea_bodega_id" FOREIGN KEY ("bodega_id") REFERENCES public."bodega" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_venta_linea_impuesto_id" FOREIGN KEY ("impuesto_id") REFERENCES public."impuesto" ("id") ON DELETE RESTRICT,
    CONSTRAINT "ck_venta_linea_cantidad_min" CHECK ("cantidad" >= 1),
    CONSTRAINT "ck_venta_linea_precio_unitario_min" CHECK ("precio_unitario" >= 0),
    CONSTRAINT "ck_venta_linea_tasa_impuesto_aplicada_min" CHECK ("tasa_impuesto_aplicada" >= 0),
    CONSTRAINT "ck_venta_linea_importe_impuesto_min" CHECK ("importe_impuesto" >= 0),
    CONSTRAINT "ck_venta_linea_subtotal_linea_min" CHECK ("subtotal_linea" >= 0),
    CONSTRAINT "ck_venta_linea_total_linea_min" CHECK ("total_linea" >= 0)
);

CREATE INDEX IF NOT EXISTS "ix_saldo_inventario_1_producto_id" ON public."saldo_inventario" ("producto_id");

CREATE INDEX IF NOT EXISTS "ix_precio_producto_1_producto_id_moneda_id_vigente_desde" ON public."precio_producto" ("producto_id", "moneda_id", "vigente_desde");

CREATE INDEX IF NOT EXISTS "ix_impuesto_1_pais_id_categoria_id_vigente_desde" ON public."impuesto" ("pais_id", "categoria_id", "vigente_desde");

CREATE INDEX IF NOT EXISTS "ix_movimiento_inventario_1_producto_id_fecha_utc" ON public."movimiento_inventario" ("producto_id", "fecha_utc");

CREATE INDEX IF NOT EXISTS "ix_movimiento_inventario_2_bodega_id_fecha_utc" ON public."movimiento_inventario" ("bodega_id", "fecha_utc");

CREATE INDEX IF NOT EXISTS "ix_movimiento_inventario_3_usuario_id_fecha_utc" ON public."movimiento_inventario" ("usuario_id", "fecha_utc");

CREATE INDEX IF NOT EXISTS "ix_ajuste_inventario_1_estado_solicitado_en_utc" ON public."ajuste_inventario" ("estado", "solicitado_en_utc");

CREATE INDEX IF NOT EXISTS "ix_ajuste_inventario_2_producto_id_bodega_id" ON public."ajuste_inventario" ("producto_id", "bodega_id");

CREATE INDEX IF NOT EXISTS "ix_parametro_1_clave_ambito_pais_id_vigente_desde" ON public."parametro" ("clave", "ambito", "pais_id", "vigente_desde");

CREATE INDEX IF NOT EXISTS "ix_tipo_cambio_1_moneda_origen_id_moneda_destino_id__a58f3ecab9" ON public."tipo_cambio" ("moneda_origen_id", "moneda_destino_id", "vigente_desde_utc");

CREATE INDEX IF NOT EXISTS "ix_proveedor_1_razon_social_pais_id" ON public."proveedor" ("razon_social", "pais_id");

CREATE INDEX IF NOT EXISTS "ix_orden_compra_1_proveedor_id_estado_creada_en_utc" ON public."orden_compra" ("proveedor_id", "estado", "creada_en_utc");

CREATE INDEX IF NOT EXISTS "ix_entrada_inventario_1_orden_compra_id_recibida_en_utc" ON public."entrada_inventario" ("orden_compra_id", "recibida_en_utc");

CREATE INDEX IF NOT EXISTS "ix_entrada_inventario_linea_1_entrada_inventario_id_producto_id" ON public."entrada_inventario_linea" ("entrada_inventario_id", "producto_id");

CREATE INDEX IF NOT EXISTS "ix_entrada_inventario_linea_2_orden_compra_linea_id" ON public."entrada_inventario_linea" ("orden_compra_linea_id");

CREATE INDEX IF NOT EXISTS "ix_cliente_1_pais_id_clasificacion" ON public."cliente" ("pais_id", "clasificacion");

CREATE INDEX IF NOT EXISTS "ix_cliente_2_correo" ON public."cliente" ("correo");

CREATE INDEX IF NOT EXISTS "ix_auditoria_1_entidad_registro_id_fecha_utc" ON public."auditoria" ("entidad", "registro_id", "fecha_utc");

CREATE INDEX IF NOT EXISTS "ix_auditoria_2_usuario_id_fecha_utc" ON public."auditoria" ("usuario_id", "fecha_utc");

CREATE INDEX IF NOT EXISTS "ix_venta_1_pais_id_creada_en_utc" ON public."venta" ("pais_id", "creada_en_utc");

CREATE INDEX IF NOT EXISTS "ix_venta_2_estado_creada_en_utc" ON public."venta" ("estado", "creada_en_utc");

CREATE INDEX IF NOT EXISTS "ix_venta_3_cliente_id_creada_en_utc" ON public."venta" ("cliente_id", "creada_en_utc");

CREATE INDEX IF NOT EXISTS "ix_venta_linea_1_venta_id_producto_id" ON public."venta_linea" ("venta_id", "producto_id");

CREATE OR REPLACE FUNCTION public.rechazar_cambio_inmutable() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'La tabla % es inmutable', TG_TABLE_NAME USING ERRCODE = '55000'; END; $$;

DROP TRIGGER IF EXISTS "trg_movimiento_inventario_inmutable" ON public."movimiento_inventario";

CREATE TRIGGER "trg_movimiento_inventario_inmutable" BEFORE UPDATE OR DELETE ON public."movimiento_inventario" FOR EACH ROW EXECUTE FUNCTION public.rechazar_cambio_inmutable();

DROP TRIGGER IF EXISTS "trg_auditoria_inmutable" ON public."auditoria";

CREATE TRIGGER "trg_auditoria_inmutable" BEFORE UPDATE OR DELETE ON public."auditoria" FOR EACH ROW EXECUTE FUNCTION public.rechazar_cambio_inmutable();

DROP TRIGGER IF EXISTS "trg_migracion_historial_inmutable" ON public."migracion_historial";

CREATE TRIGGER "trg_migracion_historial_inmutable" BEFORE UPDATE OR DELETE ON public."migracion_historial" FOR EACH ROW EXECUTE FUNCTION public.rechazar_cambio_inmutable();

CREATE OR REPLACE FUNCTION public.rechazar_eliminacion_producto() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Los productos solo se pueden desactivar' USING ERRCODE = '55000'; END; $$;

DROP TRIGGER IF EXISTS "trg_producto_sin_delete" ON public."producto";

CREATE TRIGGER "trg_producto_sin_delete" BEFORE DELETE ON public."producto" FOR EACH ROW EXECUTE FUNCTION public.rechazar_eliminacion_producto();

DROP TRIGGER IF EXISTS "trg_bodega_sin_delete" ON public."bodega";

CREATE TRIGGER "trg_bodega_sin_delete" BEFORE DELETE ON public."bodega" FOR EACH ROW EXECUTE FUNCTION public.rechazar_eliminacion_producto();
