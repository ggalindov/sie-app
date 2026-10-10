package sie.siejuridicos.registro;

// Agrupador de alto nivel para los TipoRegistroSistema -- pedido explícito del usuario:
// "pon categorias para que sea mas intuitivo de entender". Antes /admin/registro mostraba
// los 14 tipos como una sola fila plana de filtros sin ningún agrupamiento; ahora cada tipo
// pertenece a una de estas 4 categorías, usadas tanto para agrupar los filtros como para
// darle un color propio a cada fila en el panel.
public enum CategoriaRegistroSistema {
    COMUNICACIONES("Comunicaciones automáticas"),
    SINCRONIZACION("Sincronización de datos"),
    SEGURIDAD("Seguridad y usuarios"),
    GESTION_INTERNA("Gestión interna");

    private final String nombreVisible;

    CategoriaRegistroSistema(String nombreVisible) {
        this.nombreVisible = nombreVisible;
    }

    public String getNombreVisible() {
        return nombreVisible;
    }
}
