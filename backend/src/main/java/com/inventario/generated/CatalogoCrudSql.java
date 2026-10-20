// GENERADO — no editar; modifique el JSON del modelo o la plantilla.
package com.inventario.generated;

public final class CatalogoCrudSql {
    private CatalogoCrudSql() {
    }

    public static final String CATEGORIA_COLUMNS =
            "\"id\", \"codigo\", \"nombre\", \"padre_id\", \"activo\"";
    public static final String CATEGORIA_SELECT_ALL =
            "SELECT \"id\", \"codigo\", \"nombre\", \"padre_id\", \"activo\" FROM " +
            "public.\"categoria\"";
    public static final String CATEGORIA_SELECT_BY_ID =
            "SELECT \"id\", \"codigo\", \"nombre\", \"padre_id\", \"activo\" FROM " +
            "public.\"categoria\" WHERE \"id\" = ?";
    public static final String CATEGORIA_INSERT =
            "INSERT INTO public.\"categoria\" (\"id\", \"codigo\", \"nombre\", " +
            "\"padre_id\", \"activo\") VALUES (?, ?, ?, ?, ?)";
    public static final String CATEGORIA_UPDATE =
            "UPDATE public.\"categoria\" SET \"codigo\" = ?, \"nombre\" = ?, \"padre_id\" " +
            "= ?, \"activo\" = ? WHERE \"id\" = ?";
    public static final String CATEGORIA_DELETE =
            "DELETE FROM public.\"categoria\" WHERE \"id\" = ?";

    public static final String TIPO_PRODUCTO_COLUMNS =
            "\"id\", \"codigo\", \"nombre\", \"activo\"";
    public static final String TIPO_PRODUCTO_SELECT_ALL =
            "SELECT \"id\", \"codigo\", \"nombre\", \"activo\" FROM " +
            "public.\"tipo_producto\"";
    public static final String TIPO_PRODUCTO_SELECT_BY_ID =
            "SELECT \"id\", \"codigo\", \"nombre\", \"activo\" FROM " +
            "public.\"tipo_producto\" WHERE \"id\" = ?";
    public static final String TIPO_PRODUCTO_INSERT =
            "INSERT INTO public.\"tipo_producto\" (\"id\", \"codigo\", \"nombre\", " +
            "\"activo\") VALUES (?, ?, ?, ?)";
    public static final String TIPO_PRODUCTO_UPDATE =
            "UPDATE public.\"tipo_producto\" SET \"codigo\" = ?, \"nombre\" = ?, " +
            "\"activo\" = ? WHERE \"id\" = ?";
    public static final String TIPO_PRODUCTO_DELETE =
            "DELETE FROM public.\"tipo_producto\" WHERE \"id\" = ?";

    public static final String ATRIBUTO_DEFINICION_COLUMNS =
            "\"id\", \"tipo_producto_id\", \"nombre\", \"tipo_dato\", \"obligatorio\", " +
            "\"opciones\"";
    public static final String ATRIBUTO_DEFINICION_SELECT_ALL =
            "SELECT \"id\", \"tipo_producto_id\", \"nombre\", \"tipo_dato\", " +
            "\"obligatorio\", \"opciones\" FROM public.\"atributo_definicion\"";
    public static final String ATRIBUTO_DEFINICION_SELECT_BY_ID =
            "SELECT \"id\", \"tipo_producto_id\", \"nombre\", \"tipo_dato\", " +
            "\"obligatorio\", \"opciones\" FROM public.\"atributo_definicion\" WHERE " +
            "\"id\" = ?";
    public static final String ATRIBUTO_DEFINICION_INSERT =
            "INSERT INTO public.\"atributo_definicion\" (\"id\", \"tipo_producto_id\", " +
            "\"nombre\", \"tipo_dato\", \"obligatorio\", \"opciones\") VALUES (?, ?, ?, " +
            "?, ?, ?)";
    public static final String ATRIBUTO_DEFINICION_UPDATE =
            "UPDATE public.\"atributo_definicion\" SET \"tipo_producto_id\" = ?, " +
            "\"nombre\" = ?, \"tipo_dato\" = ?, \"obligatorio\" = ?, \"opciones\" = ? " +
            "WHERE \"id\" = ?";
    public static final String ATRIBUTO_DEFINICION_DELETE =
            "DELETE FROM public.\"atributo_definicion\" WHERE \"id\" = ?";

    public static final String PRODUCTO_COLUMNS =
            "\"id\", \"codigo_interno\", \"tipo_producto_id\", \"categoria_id\", " +
            "\"subcategoria_id\", \"atributos\", \"activo\", \"creado_en_utc\", " +
            "\"actualizado_en_utc\"";
    public static final String PRODUCTO_SELECT_ALL =
            "SELECT \"id\", \"codigo_interno\", \"tipo_producto_id\", \"categoria_id\", " +
            "\"subcategoria_id\", \"atributos\", \"activo\", \"creado_en_utc\", " +
            "\"actualizado_en_utc\" FROM public.\"producto\"";
    public static final String PRODUCTO_SELECT_BY_ID =
            "SELECT \"id\", \"codigo_interno\", \"tipo_producto_id\", \"categoria_id\", " +
            "\"subcategoria_id\", \"atributos\", \"activo\", \"creado_en_utc\", " +
            "\"actualizado_en_utc\" FROM public.\"producto\" WHERE \"id\" = ?";
    public static final String PRODUCTO_INSERT =
            "INSERT INTO public.\"producto\" (\"id\", \"codigo_interno\", " +
            "\"tipo_producto_id\", \"categoria_id\", \"subcategoria_id\", \"atributos\", " +
            "\"activo\", \"creado_en_utc\", \"actualizado_en_utc\") VALUES (?, ?, ?, ?, " +
            "?, ?, ?, ?, ?)";
    public static final String PRODUCTO_UPDATE =
            "UPDATE public.\"producto\" SET \"codigo_interno\" = ?, " +
            "\"tipo_producto_id\" = ?, \"categoria_id\" = ?, \"subcategoria_id\" = ?, " +
            "\"atributos\" = ?, \"activo\" = ?, \"creado_en_utc\" = ?, " +
            "\"actualizado_en_utc\" = ? WHERE \"id\" = ?";

}
