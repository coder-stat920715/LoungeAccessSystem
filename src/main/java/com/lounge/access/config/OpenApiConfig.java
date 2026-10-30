package com.lounge.access.config;

import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(name = "basicAuth", type = SecuritySchemeType.HTTP, scheme = "basic")
public class OpenApiConfig {

    @Bean
    public OpenAPI loungeOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Airport Lounge Access Verification & Analytics API")
                        .version("1.0.0")
                        .description("Spend-linked lounge eligibility checks, pass redemption and real-time analytics."))
                .addSecurityItem(new SecurityRequirement().addList("basicAuth"));
    }
}
