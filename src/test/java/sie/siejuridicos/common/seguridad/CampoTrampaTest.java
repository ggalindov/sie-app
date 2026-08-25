package sie.siejuridicos.common.seguridad;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampoTrampaTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void unEnvioHumanoNuncaLlenaElCampo(String valorVacio) {
        assertFalse(CampoTrampa.esBot(valorVacio));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://spam.example", "cualquier-cosa", " x"})
    void unBotQueAutocompletaSeDetecta(String valorRelleno) {
        assertTrue(CampoTrampa.esBot(valorRelleno));
    }
}
