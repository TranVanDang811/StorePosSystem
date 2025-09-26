package com.possystem.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI apiInfo(@Value("${open.api.title}")String title,
                           @Value("${open.api.version}")String version,
                           @Value("${open.api.description}")String description,
                           @Value("${open.api.serverUrl}")String serverUrl,
                           @Value("${open.api.serverName}")String serverName){
        return new OpenAPI()
                .info(new Info()
                        .title(title)
                        .description(description)
                        .version(version)
                        .contact(new Contact()
                                .name("VanDangCoder")
                                .email("tranvandang08112003a@gmail.com")
                                .url("https://github.com/TranVanDang811"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://springdoc.org")))
                        .servers(List.of(new Server().url(serverUrl).description(serverName)))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .in(SecurityScheme.In.HEADER)
                                        .name("Authorization")
                        )
                )
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));

    }
}
