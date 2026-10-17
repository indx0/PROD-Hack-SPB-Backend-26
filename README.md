# MoverMate Backend

API для переезда без хаоса: план задач, комнаты, коробки, поиск вещей и контроль распаковки.
Java 17 · Spring Boot · PostgreSQL · JWT.

ML-опись по фотографии через LLM Proxy: описание одного главного предмета или список
до 100 вещей с метками «хрупкое», «первый день» и «перевозить с собой».
Пользователь проверяет результат и сохраняет выбранные вещи отдельными запросами.

[Swagger](https://hack-lead-01-ru3-vm1-3a8de1.vmaas-spirit.t-education.com/docs) ·
[OpenAPI JSON](https://hack-lead-01-ru3-vm1-3a8de1.vmaas-spirit.t-education.com/v3/api-docs) ·
[Здоровье сервиса](https://hack-lead-01-ru3-vm1-3a8de1.vmaas-spirit.t-education.com/actuator/health/readiness) ·
[Пайплайны](https://gitlab.t-education.com/hack-led-01/backend/-/pipelines)

В Swagger кнопка «Осень» справа внизу включает тёплую тему и полупрозрачные падающие листья.
Выбор сохраняется в браузере; при настройке уменьшения движения остаётся только тема без анимации.

## Что прочитать дальше

| Вопрос | Документ |
| --- | --- |
| Как реализованы требования на backend и в мобильном приложении? | [Реализация требований](docs/requirements.md) |
| Как устроен backend и какой стек используется? | [Архитектура](docs/architecture.md) |
| Где находятся отчёты и как запустить тесты? | [Тесты и отчёты](TESTING.md) |

Контракт относительных сроков и переноса даты переезда: [docs/task-deadlines.md](docs/task-deadlines.md).

Личные наборы и добровольный выбор задач при создании переезда: [docs/task-sets.md](docs/task-sets.md).
