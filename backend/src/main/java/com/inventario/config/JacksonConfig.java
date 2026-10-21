package com.inventario.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.math.BigDecimal;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Importes, tasas y cantidades decimales viajan en la API como texto decimal
 * (por ejemplo "1250.0000"), sin notación científica, para que ningún cliente
 * los convierta a coma flotante. Los valores JSON libres (parámetros, atributos)
 * conservan su tipo JSON.
 */
@Configuration
public class JacksonConfig {

    @Bean
    Jackson2ObjectMapperBuilderCustomizer decimalComoTexto() {
        return builder -> builder.serializerByType(BigDecimal.class, new DecimalTextoSerializer());
    }

    static final class DecimalTextoSerializer extends JsonSerializer<BigDecimal> {

        @Override
        public void serialize(BigDecimal value, JsonGenerator generator, SerializerProvider provider)
                throws IOException {
            generator.writeString(value.toPlainString());
        }
    }
}
