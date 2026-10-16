package ke.ledgercore.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.PostMapping;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("Ledger Core API").version("1.0"))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }

    /**
     * Adds a header parameter for Idempotency-Key to all POST operations. This satisfies the OpenAPI
     * documentation requirement for money‑moving endpoints (which all use POST).
     */
    @Bean
    public OperationCustomizer idempotencyHeaderCustomizer() {
        return (operation, handlerMethod) -> {
            if (handlerMethod.hasMethodAnnotation(PostMapping.class)) {
                Parameter idempotency = new Parameter()
                        .in("header")
                        .name("Idempotency-Key")
                        .description("Idempotency key to avoid duplicate processing")
                        .required(false)
                        .example("123e4567-e89b-12d3-a456-426614174000");
                operation.addParametersItem(idempotency);
            }
            return operation;
        };
    }
}
