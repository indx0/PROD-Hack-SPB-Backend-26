package ru.example.prodbackend.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexPageTransformer;
import org.springdoc.webmvc.ui.SwaggerIndexTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
public class SwaggerCopyButtonConfig {

    @Bean
    SwaggerIndexTransformer swaggerIndexTransformer(
            SwaggerUiConfigProperties uiProperties,
            SwaggerUiOAuthProperties oauthProperties,
            SwaggerWelcomeCommon welcomeCommon,
            ObjectMapperProvider objectMapperProvider
    ) {
        return new SwaggerIndexPageTransformer(uiProperties, oauthProperties, welcomeCommon, objectMapperProvider) {
            @Override
            public Resource transform(
                    HttpServletRequest request,
                    Resource resource,
                    ResourceTransformerChain chain
            ) throws IOException {
                Resource transformed = super.transform(request, resource, chain);

                if (!"swagger-initializer.js".equals(resource.getFilename())) return transformed;

                String script = new String(transformed.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

                script += """
                        
                        ;(() => {
                            const customScript = document.createElement("script");
                            customScript.src = "/js/openapi-to-markdown.js";
                            document.body.appendChild(customScript);
                        })();
                        """;

                return new TransformedResource(
                        resource,
                        script.getBytes(StandardCharsets.UTF_8)
                );
            }
        };
    }
}