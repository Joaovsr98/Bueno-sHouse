package com.restaurante.sistema.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentacao da API (RNF11), publica em /swagger-ui.html. O botao "Authorize"
 * aceita o token JWT do POST /api/auth/login para testar os endpoints protegidos.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI apiDocs() {
        return new OpenAPI()
                .info(new Info()
                        .title("Bueno's House API")
                        .version("1.0")
                        .description("Cardapio, pedidos, cozinha, caixa, estoque, fornecedores e relatorios."))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
