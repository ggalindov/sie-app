package sie.siejuridicos.categoria.dto;

import sie.siejuridicos.categoria.Categoria;

public record CategoriaResponse(
        Long id,
        String nombre,
        String slug
) {
    public static CategoriaResponse desde(Categoria categoria) {
        return new CategoriaResponse(categoria.getId(), categoria.getNombre(), categoria.getSlug());
    }
}
