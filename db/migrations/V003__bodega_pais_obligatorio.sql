-- GENERADO — no editar; modifique el JSON del modelo o la plantilla.
-- Alinea bodega.pais_id con el modelo (obligatorio). Antes de aplicarla,
-- asigne un país a cada bodega existente; si falta alguno, la migración se detiene.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM public."bodega" WHERE "pais_id" IS NULL) THEN
        RAISE EXCEPTION 'Hay bodegas sin país asignado. Asigne pais_id a cada bodega antes de aplicar V003.';
    END IF;
END;
$$;

ALTER TABLE public."bodega"
    ALTER COLUMN "pais_id" SET NOT NULL;
