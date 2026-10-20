// GENERADO — no editar; modifique el JSON del modelo o la plantilla.
package com.inventario.generated;

public final class ParametrizacionCrudSql {
    private ParametrizacionCrudSql() {
    }

    public static final String PAIS_COLUMNS =
            "\"id\", \"codigo_iso\", \"nombre\", \"moneda_local_id\", " +
            "\"idioma_predeterminado_id\", \"zona_horaria\", \"habilitado\"";
    public static final String PAIS_SELECT_ALL =
            "SELECT \"id\", \"codigo_iso\", \"nombre\", \"moneda_local_id\", " +
            "\"idioma_predeterminado_id\", \"zona_horaria\", \"habilitado\" FROM " +
            "public.\"pais\"";
    public static final String PAIS_SELECT_BY_ID =
            "SELECT \"id\", \"codigo_iso\", \"nombre\", \"moneda_local_id\", " +
            "\"idioma_predeterminado_id\", \"zona_horaria\", \"habilitado\" FROM " +
            "public.\"pais\" WHERE \"id\" = ?";
    public static final String PAIS_INSERT =
            "INSERT INTO public.\"pais\" (\"id\", \"codigo_iso\", \"nombre\", " +
            "\"moneda_local_id\", \"idioma_predeterminado_id\", \"zona_horaria\", " +
            "\"habilitado\") VALUES (?, ?, ?, ?, ?, ?, ?)";
    public static final String PAIS_UPDATE =
            "UPDATE public.\"pais\" SET \"codigo_iso\" = ?, \"nombre\" = ?, " +
            "\"moneda_local_id\" = ?, \"idioma_predeterminado_id\" = ?, " +
            "\"zona_horaria\" = ?, \"habilitado\" = ? WHERE \"id\" = ?";
    public static final String PAIS_DELETE =
            "DELETE FROM public.\"pais\" WHERE \"id\" = ?";

    public static final String MONEDA_COLUMNS =
            "\"id\", \"codigo_iso\", \"nombre\", \"simbolo\", \"decimales\", \"formato\", " +
            "\"regla_redondeo\"";
    public static final String MONEDA_SELECT_ALL =
            "SELECT \"id\", \"codigo_iso\", \"nombre\", \"simbolo\", \"decimales\", " +
            "\"formato\", \"regla_redondeo\" FROM public.\"moneda\"";
    public static final String MONEDA_SELECT_BY_ID =
            "SELECT \"id\", \"codigo_iso\", \"nombre\", \"simbolo\", \"decimales\", " +
            "\"formato\", \"regla_redondeo\" FROM public.\"moneda\" WHERE \"id\" = ?";
    public static final String MONEDA_INSERT =
            "INSERT INTO public.\"moneda\" (\"id\", \"codigo_iso\", \"nombre\", " +
            "\"simbolo\", \"decimales\", \"formato\", \"regla_redondeo\") VALUES (?, ?, " +
            "?, ?, ?, ?, ?)";
    public static final String MONEDA_UPDATE =
            "UPDATE public.\"moneda\" SET \"codigo_iso\" = ?, \"nombre\" = ?, \"simbolo\" " +
            "= ?, \"decimales\" = ?, \"formato\" = ?, \"regla_redondeo\" = ? WHERE \"id\" " +
            "= ?";
    public static final String MONEDA_DELETE =
            "DELETE FROM public.\"moneda\" WHERE \"id\" = ?";

    public static final String IDIOMA_COLUMNS =
            "\"id\", \"codigo_bcp47\", \"nombre\", \"habilitado\"";
    public static final String IDIOMA_SELECT_ALL =
            "SELECT \"id\", \"codigo_bcp47\", \"nombre\", \"habilitado\" FROM " +
            "public.\"idioma\"";
    public static final String IDIOMA_SELECT_BY_ID =
            "SELECT \"id\", \"codigo_bcp47\", \"nombre\", \"habilitado\" FROM " +
            "public.\"idioma\" WHERE \"id\" = ?";
    public static final String IDIOMA_INSERT =
            "INSERT INTO public.\"idioma\" (\"id\", \"codigo_bcp47\", \"nombre\", " +
            "\"habilitado\") VALUES (?, ?, ?, ?)";
    public static final String IDIOMA_UPDATE =
            "UPDATE public.\"idioma\" SET \"codigo_bcp47\" = ?, \"nombre\" = ?, " +
            "\"habilitado\" = ? WHERE \"id\" = ?";
    public static final String IDIOMA_DELETE =
            "DELETE FROM public.\"idioma\" WHERE \"id\" = ?";

    public static final String TIPO_CAMBIO_COLUMNS =
            "\"id\", \"moneda_origen_id\", \"moneda_destino_id\", \"tasa\", " +
            "\"vigente_desde_utc\", \"origen\", \"actualizado_por_id\"";
    public static final String TIPO_CAMBIO_SELECT_ALL =
            "SELECT \"id\", \"moneda_origen_id\", \"moneda_destino_id\", \"tasa\", " +
            "\"vigente_desde_utc\", \"origen\", \"actualizado_por_id\" FROM " +
            "public.\"tipo_cambio\"";
    public static final String TIPO_CAMBIO_SELECT_BY_ID =
            "SELECT \"id\", \"moneda_origen_id\", \"moneda_destino_id\", \"tasa\", " +
            "\"vigente_desde_utc\", \"origen\", \"actualizado_por_id\" FROM " +
            "public.\"tipo_cambio\" WHERE \"id\" = ?";
    public static final String TIPO_CAMBIO_INSERT =
            "INSERT INTO public.\"tipo_cambio\" (\"id\", \"moneda_origen_id\", " +
            "\"moneda_destino_id\", \"tasa\", \"vigente_desde_utc\", \"origen\", " +
            "\"actualizado_por_id\") VALUES (?, ?, ?, ?, ?, ?, ?)";
    public static final String TIPO_CAMBIO_UPDATE =
            "UPDATE public.\"tipo_cambio\" SET \"moneda_origen_id\" = ?, " +
            "\"moneda_destino_id\" = ?, \"tasa\" = ?, \"vigente_desde_utc\" = ?, " +
            "\"origen\" = ?, \"actualizado_por_id\" = ? WHERE \"id\" = ?";
    public static final String TIPO_CAMBIO_DELETE =
            "DELETE FROM public.\"tipo_cambio\" WHERE \"id\" = ?";

    public static final String IMPUESTO_COLUMNS =
            "\"id\", \"pais_id\", \"categoria_id\", \"nombre\", \"tasa\", " +
            "\"incluido_en_precio\", \"vigente_desde\", \"vigente_hasta\"";
    public static final String IMPUESTO_SELECT_ALL =
            "SELECT \"id\", \"pais_id\", \"categoria_id\", \"nombre\", \"tasa\", " +
            "\"incluido_en_precio\", \"vigente_desde\", \"vigente_hasta\" FROM " +
            "public.\"impuesto\"";
    public static final String IMPUESTO_SELECT_BY_ID =
            "SELECT \"id\", \"pais_id\", \"categoria_id\", \"nombre\", \"tasa\", " +
            "\"incluido_en_precio\", \"vigente_desde\", \"vigente_hasta\" FROM " +
            "public.\"impuesto\" WHERE \"id\" = ?";
    public static final String IMPUESTO_INSERT =
            "INSERT INTO public.\"impuesto\" (\"id\", \"pais_id\", \"categoria_id\", " +
            "\"nombre\", \"tasa\", \"incluido_en_precio\", \"vigente_desde\", " +
            "\"vigente_hasta\") VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    public static final String IMPUESTO_UPDATE =
            "UPDATE public.\"impuesto\" SET \"pais_id\" = ?, \"categoria_id\" = ?, " +
            "\"nombre\" = ?, \"tasa\" = ?, \"incluido_en_precio\" = ?, \"vigente_desde\" " +
            "= ?, \"vigente_hasta\" = ? WHERE \"id\" = ?";
    public static final String IMPUESTO_DELETE =
            "DELETE FROM public.\"impuesto\" WHERE \"id\" = ?";

}
