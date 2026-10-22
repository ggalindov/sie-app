package sie.siejuridicos.documento;

import org.junit.jupiter.api.Test;
import sie.siejuridicos.common.exception.EntidadInvalidaException;
import sie.siejuridicos.crm.ClienteCrm;
import sie.siejuridicos.crm.TipoClienteCrm;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Regresión de un bug real encontrado en la auditoría en vivo del motor de firma (2026-10-18):
// la regla "una EMPRESA solo puede recibir el enlace por correo" (DocumentoFirmaService.
// ParteAFirmar) lanzaba IllegalArgumentException, que el manejador global de excepciones no
// traduce a una respuesta 4xx -- un gerente que intentaba asignar WhatsApp a una empresa
// recibía un 500 "Ocurrió un error inesperado" genérico en vez del mensaje real. Confirmado
// en vivo contra el backend real antes y después del fix. Esta prueba fija el tipo de
// excepción correcto para que no vuelva a romperse en silencio.
class ParteAFirmarTest {

    private ClienteCrm clienteDe(TipoClienteCrm tipo) {
        ClienteCrm cliente = new ClienteCrm();
        cliente.setTipo(tipo);
        cliente.setNombre("Cliente de prueba");
        return cliente;
    }

    @Test
    void unaEmpresaConCanalWhatsappLanzaEntidadInvalidaExceptionNoIllegalArgumentException() {
        ClienteCrm empresa = clienteDe(TipoClienteCrm.EMPRESA);

        assertThrows(EntidadInvalidaException.class,
                () -> new DocumentoFirmaService.ParteAFirmar(empresa, DocumentoFirmante.CANAL_WHATSAPP));
    }

    @Test
    void unaEmpresaConCanalEmailSeAceptaSinError() {
        ClienteCrm empresa = clienteDe(TipoClienteCrm.EMPRESA);

        assertDoesNotThrow(() -> new DocumentoFirmaService.ParteAFirmar(empresa, DocumentoFirmante.CANAL_EMAIL));
    }

    @Test
    void unaPersonaNaturalPuedeUsarCualquieraDeLosDosCanales() {
        ClienteCrm persona = clienteDe(TipoClienteCrm.PERSONA_NATURAL);

        assertDoesNotThrow(() -> new DocumentoFirmaService.ParteAFirmar(persona, DocumentoFirmante.CANAL_WHATSAPP));
        assertDoesNotThrow(() -> new DocumentoFirmaService.ParteAFirmar(persona, DocumentoFirmante.CANAL_EMAIL));
    }
}
