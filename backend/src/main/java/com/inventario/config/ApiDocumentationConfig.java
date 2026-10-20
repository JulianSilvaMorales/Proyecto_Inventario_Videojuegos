package com.inventario.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiDocumentationConfig {

    @Bean
    OpenAPI inventarioOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("API de inventario")
                        .description("API para el sistema de inventario de videojuegos.")
                        .version("v1"));
    }
}
