# Архитектура backend

## Структура

Модульный монолит: одно Spring Boot-приложение, пакеты по предметным областям.
Для масштаба MVP это позволяет выполнять связанные изменения одной транзакцией
и не требует отдельных сервисов, очередей и межсервисной синхронизации.

```mermaid
flowchart LR
    Client["Клиент / Swagger"] --> Nginx["Nginx · HTTPS"]
    Nginx --> Security["Spring Security · JWT"]
    subgraph Backend["Spring Boot · один процесс"]
        Security --> Controller["Контроллеры и DTO"]
        Controller --> Service["Бизнес-сервисы · транзакции"]
        Service --> Repository["JPA-репозитории"]
    end
    Repository --> DB[("PostgreSQL")]
```

## Стек

- Java 17 / Spring Boot
- PostgreSQL / JPA / Liquibase
- Spring Security / JWT access + refresh
- Springdoc / Swagger

Схема БД обновляется SQL-миграциями Liquibase. Hibernate проверяет её соответствие сущностям через `ddl-auto=validate`.

Изменение статуса коробки и операции с её вещами используют общую блокировку строки коробки
(`PESSIMISTIC_WRITE`). Проверка отметок и смена статуса выполняются в одной транзакции:
одновременное добавление вещи не позволяет закрыть коробку с неготовым содержимым.
Счётчики готовности вычисляются агрегатным запросом по записям вещей; для списка коробок
используется один групповой запрос по идентификаторам коробок текущей страницы.

Связь пункта первого дня с вещью хранится по UUID: уникальный внешний ключ с `ON DELETE SET NULL`
оставляет пункт при удалении вещи. Привязка и снятие `firstDay` блокируют сначала коробку,
затем пункт чек-листа и меняют данные одной транзакцией. Список чек-листа загружает вещь,
коробку и комнату через `EntityGraph`, чтобы избежать запроса на каждый пункт.

Перенос меняет `box_id` существующей вещи одной транзакцией. Исходная и целевая коробки блокируются
в фиксированном порядке UUID, поэтому встречные переносы не создают цикл блокировок.
Расположение проверяется после блокировки. Если коробка изменилась во время привязки к чек-листу,
привязка возвращает 409 и требует повтора, не захватывая новую коробку после старой.

## Пакеты

| Пакеты | Ответственность |
| --- | --- |
| [move](../src/main/java/ru/example/prodbackend/move), [room](../src/main/java/ru/example/prodbackend/room) | Проекты, проверка владельца, комнаты |
| [box](../src/main/java/ru/example/prodbackend/box), [item](../src/main/java/ru/example/prodbackend/item) | Коробки, статусы, содержимое, поиск и подсказки |
| [task](../src/main/java/ru/example/prodbackend/task), [checklist](../src/main/java/ru/example/prodbackend/checklist) | План, шаблоны, первый день |
| [event](../src/main/java/ru/example/prodbackend/event), [progress](../src/main/java/ru/example/prodbackend/progress) | История действий и сводки |
| [auth](../src/main/java/ru/example/prodbackend/auth), [user](../src/main/java/ru/example/prodbackend/user) | Вход, токены и профиль |
| [common](../src/main/java/ru/example/prodbackend/common), [exception](../src/main/java/ru/example/prodbackend/exception) | Пагинация и единый формат ошибок |

## Что прочитать дальше

| Вопрос | Документ |
| --- | --- |
| Где открыть API и пайплайны? | [README](../README.md) |
| Как реализованы требования на backend и в мобильном приложении? | [Реализация требований](requirements.md) |
| Где находятся отчёты и как запустить тесты? | [Тесты и отчёты](../TESTING.md) |
