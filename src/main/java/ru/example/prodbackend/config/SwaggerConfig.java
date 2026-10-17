package ru.example.prodbackend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(
        name = "jwtAuth",
        description = "Токен доступа JWT, полученный при входе или обновлении сессии. Введите токен без префикса Bearer.",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
@OpenAPIDefinition(
        info = @Info(
                title = "MoverMate API",
                description = """
                        API для планирования переезда, совместной работы, упаковки и поиска вещей.

                        **Пагинация.** `page` — номер страницы от 0, `size` — от 1 до 100, по умолчанию 20.
                        Ответ содержит `content`, `page`, `size`, `totalElements`, `totalPages`.
                        Порядок сортировки и доступные фильтры описаны у операции.

                        **Ошибки.** Тело `ErrorResponse` содержит английский машинный `code`, сообщение `message`,
                        идентификатор `traceId`, время `time` и при необходимости `fieldErrors`.
                        Пояснения в документации русские; сообщения реальных ответов зависят от источника ошибки.
                        Один HTTP-статус может соответствовать нескольким кодам — они перечислены в описании ответа.
                        """
        )
)
public class SwaggerConfig {
    @Bean
    OpenApiCustomizer swaggerDocumentation() {
        return openApi -> {
            openApi.setTags(SwaggerTags.all());
            if (openApi.getServers() != null) {
                openApi.getServers().forEach(server -> {
                    if ("Generated server url".equals(server.getDescription())) server.setDescription("Адрес сервера API");
                });
            }
            // JSON controllers without a runtime produces restriction are otherwise shown as */*.
            openApi.getPaths().forEach((url, path) -> {
                if (url.startsWith("/api/demo/")) return;
                path.readOperations().forEach(operation -> operation.getResponses().values().forEach(response -> {
                    var content = response.getContent();
                    if (content != null && content.containsKey("*/*")) {
                        var media = content.remove("*/*");
                        content.putIfAbsent("application/json", media);
                    }
                }));
            });
            SwaggerErrorExamples.apply(openApi);
        };
    }
}
