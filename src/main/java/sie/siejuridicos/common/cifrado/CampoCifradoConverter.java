package sie.siejuridicos.common.cifrado;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

// autoApply=false a propósito: se aplica explícitamente con @Convert en cada campo que de
// verdad necesita cifrarse (ver Cliente, Caso), nunca por defecto a todos los String de
// todas las entidades del sistema (aplicaría, por ejemplo, a columnas que sí necesitan ser
// buscables o legibles directo en SQL para reportes).
//
// @Component (no solo @Converter): Spring Boot registra su propio SpringBeanContainer para
// Hibernate, así este converter puede recibir CifradoService por inyección de constructor
// como cualquier otro bean, en vez de tener que leer una llave estática global.
@Converter(autoApply = false)
@Component
public class CampoCifradoConverter implements AttributeConverter<String, String> {

    private final CifradoService cifradoService;

    public CampoCifradoConverter(CifradoService cifradoService) {
        this.cifradoService = cifradoService;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return cifradoService.cifrar(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return cifradoService.descifrar(dbData);
    }
}
