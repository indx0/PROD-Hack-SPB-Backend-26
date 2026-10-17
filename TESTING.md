# Тесты и отчёты

## Где посмотреть результат

Результаты доступны в [GitLab → Pipelines](https://gitlab.t-education.com/hack-led-01/backend/-/pipelines)
в пайплайне выбранного коммита или MR.

- **Tests** — результаты выполнения тестов.
- **Job test → Job artifacts → Download** — архив с отчётами покрытия. HTML-файлы открываются после распаковки архива.

| Отчёт | Файл в артефактах |
| --- | --- |
| Общее покрытие | `target/site/jacoco/index.html` |
| Только интеграционные тесты | `target/site/jacoco-integration/index.html` |
| Только unit-тесты | `target/site/jacoco-unit/index.html` |
| Результаты JUnit | `target/surefire-reports/`, `target/failsafe-reports/` |

## CI/CD

```mermaid
%%{init: {"theme":"base","themeVariables":{"primaryTextColor":"#f8fafc","textColor":"#f8fafc","lineColor":"#94a3b8","clusterBkg":"#1e293b","clusterBorder":"#64748b","titleColor":"#f8fafc","edgeLabelBackground":"#1e293b"},"flowchart":{"htmlLabels":false}}}%%
flowchart TD
    MR["Merge Request"] --> Tests
    Master["Push в master"] --> Tests

    subgraph CI["CI · Проверка и сборка"]
        Tests["Сборка приложения<br/>Unit- и интеграционные тесты"] --> Reports["Отчёты JUnit и JaCoCo"]
        Reports -->|Только master| Image["Сборка и публикация<br/>Docker-образа"]
    end

    subgraph CD["CD · Деплой"]
        Deploy["Обновление приложения на ВМ"] --> Check["Проверка readiness и OpenAPI"]
    end

    Image --> Deploy

    classDef trigger fill:#312e81,stroke:#a5b4fc,color:#f8fafc
    classDef ci fill:#1e3a5f,stroke:#60a5fa,color:#f8fafc
    classDef cd fill:#14532d,stroke:#4ade80,color:#f8fafc
    class MR,Master trigger
    class Tests,Reports,Image ci
    class Deploy,Check cd
```

## Локальная проверка

Только unit-тесты:

```bash
mvn test
```

Все тесты:

```bash
mvn clean verify
```

## ML-опись по фото

Оба маршрута — `item-description` и `items-description` — проверяются без обращения
к внешнему LLM. Для тестов и сборки образа `LLM_PROXY_API_KEY` не требуется.

| Проверки | Покрытие |
| --- | --- |
| HTTP-клиент с локальным HTTP-стабом | Отдельные промпты и JSON Schema, авторизация, передача изображения, таймауты включая чтение ответа, квота, ограничение размера ответа и ошибки прокси. |
| Валидатор описаний | Названия и метки, лишние поля, пустой список, один и 100 предметов, отклонение 101 элемента, сохранение порядка и одинаковых названий; один невалидный элемент отклоняет весь список. |
| Сервисы и API | Проверка владельца до вызова LLM, JWT, форматы и размер загрузки, отсутствие/несколько файлов, единый формат ошибок и отсутствие записи при распознавании. |
| Сохранение и миграции | Сохранение/редактирование меток, значения по умолчанию для существующих вещей и полный откат схемы. |

Запустить unit-тесты модуля распознавания:

```bash
./mvnw -Dtest=HttpLlmProxyClientTest,ItemDescriptionServiceTest,PhotoValidatorTest,ItemDescriptionValidatorTest test
```

Запустить unit-тесты и интеграционные проверки API вещей и миграций:

```bash
./mvnw -Dit.test=ItemApiIntegrationTest,LiquibaseMigrationIntegrationTest verify
```

Интеграционные проверки используют PostgreSQL через Testcontainers; в CI подключается
отдельный сервис PostgreSQL. Контракт одного главного предмета проверяется регрессионными тестами.
Сценарии и ограничения ML описаны в [реализации требований](docs/requirements.md).

## Мониторинг Sage

| Проверки | Покрытие |
| --- | --- |
| Авторизация метрик | `ObservabilityIntegrationTest`: настоящий endpoint Prometheus доступен с отдельным scrape-токеном; отсутствие токена, неверное значение, пользовательский JWT и POST отклоняются. Scrape-токен не открывает пользовательское API; readiness и вход по JWT сохраняются. `SecurityIntegrationTest` проверяет закрытый доступ при отсутствии настройки токена. |
| HTTP-логи | `RequestLoggingFilterTest`: JSON разбирается, содержит статус, длительность, шаблон маршрута и уникальный `requestId`, включая ответы 401/403 и необработанное исключение. Проверяются восстановление MDC и отсутствие тела запроса, токена и исходного URL с идентификатором. |
| Метрики LLM | `HttpLlmProxyClientTest`: учёт успеха, таймаута, перегрузки, некорректного ответа и ошибки прокси; перенос `requestId` в рабочий поток и очистка контекста. При игнорировании отмены активность сохраняется до фактического завершения, без второго учёта успеха. |

Unit-проверки наблюдаемости:

```bash
./mvnw -Dtest=RequestLoggingFilterTest,HttpLlmProxyClientTest test
```

Unit-тесты и интеграционные проверки авторизации:

```bash
./mvnw -Dit.test=ObservabilityIntegrationTest,SecurityIntegrationTest verify
```

Интеграционные проверки требуют PostgreSQL (Testcontainers локально либо внешний
тестовый сервер в CI). Доступ к Sage, рабочие токены и внешний LLM для них не нужны.
`ObservabilityIntegrationTest` сохраняет снимок endpoint в
`target/observability/scrape.prom`; это артефакт тестового запуска.

Приём данных вашей группой Sage проверяется отдельно после настройки target:
см. [руководство мониторинга](docs/observability.md). Тесты приложения не проверяют
внешние права, сетевой доступ сборщика, панели и доставку уведомлений Sage.
