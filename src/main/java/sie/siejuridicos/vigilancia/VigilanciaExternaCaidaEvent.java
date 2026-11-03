package sie.siejuridicos.vigilancia;

// Publicado por VigilanciaExternaOrquestador cuando, al terminar un paso del ciclo nocturno,
// el cliente externo correspondiente (RamaJudicialCpnuClient/PublicacionesProcesalesClient/
// SamaiClient) "pareceCaido()" -- varios fallos de red o HTTP 403 consecutivos, nunca un
// simple "no encontré nada". Dispara una alerta por correo a todo ADMIN_GENERAL activo (ver
// VigilanciaCaidaAlertaListener) y queda reflejado como exitoso=false en RegistroSistema
// (ver VigilanciaExternaOrquestador.registrarPaso()).
public record VigilanciaExternaCaidaEvent(String nombreServicio, String detalle) {
}
