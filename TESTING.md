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

## Опись по фото

Тесты HTTP-клиента используют локальный HTTP-стаб: проверяются схема запроса, авторизация,
таймауты, квота и некорректные ответы. Интеграционные тесты подменяют LLM-клиент,
проверяют доступ по JWT, отсутствие записи при распознавании и сохранение/редактирование меток.
Миграционный тест проверяет добавление меток к существующей вещи и полный откат схемы.
Внешний LLM и API-ключ для CI не нужны.

Для списка предметов дополнительно проверяются отдельный промпт и JSON Schema, порядок и
повторяющиеся названия, границы 100/101 элементов, отказ всего ответа при невалидном элементе.
Проверки JWT и загрузки выполняются для обоих маршрутов; контракт одного предмета остаётся
под регрессионными тестами. Внешний LLM не вызывается.
