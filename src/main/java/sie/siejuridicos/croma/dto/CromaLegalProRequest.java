package sie.siejuridicos.croma.dto;

public record CromaLegalProRequest(
        Long casoId,
        String radicado,
        Boolean buscarBoletinesEnVivo
) {
    public boolean debeBuscarBoletinesEnVivo() {
        return buscarBoletinesEnVivo == null || Boolean.TRUE.equals(buscarBoletinesEnVivo);
    }
}
