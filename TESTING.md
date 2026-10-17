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

CI использует [настройки Maven](.ci/maven-settings.xml) с зеркалом Maven Central
в Google Cloud Storage. Это устраняет прямые скачивания с `repo.maven.apache.org`,
где общий исходящий адрес CI может получить `429 Too Many Requests`.
Локальная сборка без `-s` использует обычные настройки Maven.
Кэш `.m2/repository` сохраняется и после ошибки job (`cache:when: always`),
чтобы не терять уже скачанные зависимости.

Если Runner пишет `No URL provided, cache will not be downloaded from shared cache server`,
распределённый кэш не настроен. Его постоянное хранение между Kubernetes pod должен
настроить администратор Runner (общее хранилище или постоянный том); одной настройки
в `.gitlab-ci.yml` для этого недостаточно. Повторные запуски без паузы при 429
не устраняют ограничение. При наличии Maven-прокси площадки его URL можно указать
вместо публичного зеркала в `.ci/maven-settings.xml`.

`Non-resolvable parent POM` с HTTP 429 означает отказ загрузки до начала компиляции.
Предупреждения об отсутствии JUnit/JaCoCo/JAR в этом случае — следствие того,
что сборка ещё не дошла до создания этих файлов.

Только unit-тесты:

```bash
mvn test
```

Все тесты:

```bash
mvn clean verify
```

Проверки сроков и переноса даты переезда находятся в
`TaskRescheduleIntegrationTest`: UTC и смещения, выборочный пересчёт, FIXED и выполненные задачи,
полный отказ при ошибке, повторы, архив, доступ участников и конкурентное редактирование.
`MoveProjectBootstrapIntegrationTest` проверяет пустой план без выбора и явное применение шаблонов,
`LiquibaseMigrationIntegrationTest` — ограничения БД, сохранение дат старых задач и откат миграции.

Только эти интеграционные сценарии (также выполняются unit-тесты):

```bash
mvn -Dit.test=TaskRescheduleIntegrationTest,MoveProjectBootstrapIntegrationTest,LiquibaseMigrationIntegrationTest verify
```

`TaskSetIntegrationTest` проверяет личный CRUD, порядок, пагинацию, запрет чужого доступа,
копирование в RELATIVE-задачи, повторное и конкурентное применение, независимость копий,
мягкое удаление, атомарность создания с наборами, архив и применение участником общего проекта.
`DataLoaderIntegrationTest` проверяет явный выбор демо-набора без дублей при повторном запуске.

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
Ручная проверка качества на реальных фото и настройка ключа описаны в
[документации ML-описи](docs/photo-inventory.md).

Проверки объединения master и feature:
`MergedItemFlagsIntegrationTest` — совместимость flags/firstDay, откат противоречивых запросов,
batch и отвязка чек-листа; `MergedMigrationHistoryIntegrationTest` — обновление обеих прежних
историй Liquibase без сброса контрольных сумм и без потери данных.
