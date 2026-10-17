# MoverMate Backend

API для переезда без хаоса: план задач, комнаты, коробки, поиск вещей и контроль распаковки.
Java 17 · Spring Boot · PostgreSQL · JWT.

ML-опись по фотографии через LLM Proxy: описание одного главного предмета или список
до 100 вещей с метками «хрупкое», «первый день» и «перевозить с собой».
Пользователь проверяет результат и сохраняет выбранные вещи отдельными запросами.
[Контракты, ограничения и настройка ML](docs/photo-inventory.md).

[Swagger](https://hack-lead-01-ru3-vm1-3a8de1.vmaas-spirit.t-education.com/docs) ·
[OpenAPI JSON](https://hack-lead-01-ru3-vm1-3a8de1.vmaas-spirit.t-education.com/v3/api-docs) ·
[Здоровье сервиса](https://hack-lead-01-ru3-vm1-3a8de1.vmaas-spirit.t-education.com/actuator/health/readiness) ·
[Пайплайны](https://gitlab.t-education.com/hack-led-01/backend/-/pipelines)

## Что прочитать дальше

| Вопрос | Документ |
| --- | --- |
| Как реализованы требования на backend и в мобильном приложении? | [Реализация требований](docs/requirements.md) |
| Как устроен backend и какой стек используется? | [Архитектура](docs/architecture.md) |
| Как распознать один или несколько предметов по фото и настроить LLM? | [Опись по фото](docs/photo-inventory.md) |
| Где находятся отчёты и как запустить тесты? | [Тесты и отчёты](TESTING.md) |
