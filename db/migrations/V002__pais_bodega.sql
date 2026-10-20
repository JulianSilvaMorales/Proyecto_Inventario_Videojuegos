-- GENERADO — no editar; modifique el JSON del modelo o la plantilla.
ALTER TABLE public."bodega"
    ADD COLUMN IF NOT EXISTS "pais_id" UUID;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_bodega_pais_id'
          AND conrelid = 'public.bodega'::regclass
    ) THEN
        ALTER TABLE public."bodega"
            ADD CONSTRAINT "fk_bodega_pais_id"
            FOREIGN KEY ("pais_id")
            REFERENCES public."pais" ("id")
            ON DELETE RESTRICT;
    END IF;
END;
$$;

DROP TRIGGER IF EXISTS "trg_bodega_sin_delete" ON public."bodega";

CREATE TRIGGER "trg_bodega_sin_delete"
    BEFORE DELETE ON public."bodega"
    FOR EACH ROW
    EXECUTE FUNCTION public.rechazar_eliminacion_producto();
