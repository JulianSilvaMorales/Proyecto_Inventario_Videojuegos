// GENERADO — no editar; modifique el JSON del modelo o la plantilla.
package com.inventario.generated;

public final class InventarioCrudSql {
    private InventarioCrudSql() {
    }

    public static final String BODEGA_COLUMNS =
            "\"id\", \"codigo\", \"nombre\", \"categoria_id\", \"activo\"";
    public static final String BODEGA_SELECT_ALL =
            "SELECT \"id\", \"codigo\", \"nombre\", \"categoria_id\", \"activo\" FROM " +
            "public.\"bodega\"";
    public static final String BODEGA_SELECT_BY_ID =
            "SELECT \"id\", \"codigo\", \"nombre\", \"categoria_id\", \"activo\" FROM " +
            "public.\"bodega\" WHERE \"id\" = ?";
    public static final String BODEGA_INSERT =
            "INSERT INTO public.\"bodega\" (\"id\", \"codigo\", \"nombre\", " +
            "\"categoria_id\", \"activo\") VALUES (?, ?, ?, ?, ?)";
    public static final String BODEGA_UPDATE =
            "UPDATE public.\"bodega\" SET \"codigo\" = ?, \"nombre\" = ?, " +
            "\"categoria_id\" = ?, \"activo\" = ? WHERE \"id\" = ?";

}
