package sie.siejuridicos.cobro.dto;

// Resultado del envío de prueba (ver CobroService.enviarRecordatorioDePrueba()): un disparo
// manual de un solo recordatorio al número de prueba fijo de la firma (3126029742), totalmente
// aislado del lote real de clientes -- nunca toca a ningún cliente real ni consume el cupo
// diario compartido de envíos masivos.
public record ResumenEnvioRecordatorioPrueba(
        boolean encontrado,
        String nombre,
        boolean correoEnviado,
        boolean whatsappEnviado
) {
}
