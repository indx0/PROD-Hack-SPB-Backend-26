# MoverMate Backend

Backend MoverMate: Java 17, Spring Boot, PostgreSQL, JWT access/refresh.

- API: https://hack-lead-01-ru3-vm1-3a8de1.vmaas-spirit.t-education.com
- Swagger: https://hack-lead-01-ru3-vm1-3a8de1.vmaas-spirit.t-education.com/docs
- Подготовка ВМ и CI/CD Spirit: [DEPLOY.md](DEPLOY.md).

## Как вносить изменения

Работаем в отдельных ветках.

```bash
git switch master
git pull --ff-only origin master
git switch -c feature/task-name
```

После изменений:

```bash
git add .
git commit -m "Add feature"
git push -u origin feature/task-name
```

1. В GitLab создай **Merge Request** из своей ветки в `master`.
2. В MR автоматически запустятся unit- и интеграционные тесты. Последующие push
   в эту же ветку снова запустят проверку. Без открытого MR feature-ветки не проверяются.
3. После успешных тестов и проверки изменений мержим MR.
4. В `master` запускается новый pipeline: **тесты → сборка Docker-образа → деплой на ВМ**.
   Сборка использует Kaniko и Spirit Registry, деплой — Teleport.

## Локальный запуск

Создай `.env` в корне проекта. Значения ниже замени своими:

```dotenv
DB_URL=db_url
DB_USERNAME=prod_backend
DB_PASSWORD=replace_with_db_password
SECRET_KEY=replace_with_base64_key
ADMIN_PASSWORD=replace_with_admin_password
USER1_PASSWORD=replace_with_user1_password
```
Два последних пароля используются для стартовых аккаунтов:
`admin` с ролью `ADMIN` и `user1` с ролью `USER` (это значения поля `email`).

```bash
mvn clean verify
docker compose up -d --build --wait --wait-timeout 180
```

Локальный Swagger: http://localhost:8080/docs.
После изменения Java-кода повтори обе команды: Dockerfile использует уже собранный JAR.

Схему PostgreSQL создаёт и обновляет Liquibase перед инициализацией JPA.
Главный changelog — `src/main/resources/db/changelog/db.changelog-master.xml`,
начальные SQL-миграции — в подпапке `1.0`. Во всех профилях, включая тестовый,
Hibernate только проверяет соответствие схемы сущностям (`ddl-auto=validate`).
При повторном запуске применённые миграции не выполняются, данные сохраняются.

Начальные миграции рассчитаны на пустую БД. Для существующей схемы требуется
отдельная процедура подключения к Liquibase; автоматического baseline нет.

DataLoader идемпотентен: создаёт отсутствующих стартовых пользователей и демоданные,
не меняет пароли существующих пользователей и не дублирует демопроект,
refresh-сессии или события при повторном вызове. Шаблоны задач по-прежнему
инициализируются Java-кодом приложения.

## Проект, задачи, первый день и журнал

`POST /api/move-projects` создаёт одной транзакцией проект, 11 невыполненных задач
из локального справочника, 4 пункта первого дня и одно событие `PROJECT_CREATED`.
Дата переезда передаётся в ISO 8601 с часовым поясом. Для сроков шаблонных задач
используется календарная дата UTC:

| Задача | Смещение в днях |
| --- | --- |
| Заказать грузчиков | -14 |
| Отключить интернет | -7 |
| Разобрать мебель | -3 |
| Разморозить холодильник; собрать коробку первого дня | -1 |
| Погрузка; проверка квартиры; передача ключей | 0 |
| Распаковать первую комнату; подключить интернет | +1 |
| Разложить вещи | +3 |

Изменение даты проекта **не пересчитывает** уже созданные сроки, включая пользовательские.
Шаблоны доступны в `GET /api/move-projects/{projectId}/tasks/templates`, со смещением
`dueDateOffsetDays`. Ручное применение шаблона по-прежнему использует срок клиента.
В новом проекте все базовые шаблоны уже применены; повторное применение возвращает 409.

| Ресурс | Путь | Операции |
| --- | --- | --- |
| Первый день | `/api/move-projects/{projectId}/first-day-checklist` | GET страницы; POST нового пункта; PATCH/DELETE `/{itemId}` |
| Журнал | `/api/move-projects/{projectId}/events` | GET страницы, новые события первыми |
| Задачи | `/api/move-projects/{projectId}/tasks` | CRUD, `/{taskId}/completion`, `/templates`, `/from-template` |

Все операции требуют JWT владельца проекта. Общая пагинация: `page=0`, `size=20`,
максимум 100; ответ `content`, `page`, `size`, `totalElements`, `totalPages`.
PATCH чек-листа принимает `name` и `completed`; отсутствующие и null-поля сохраняются.
Повтор отметки выполнения не меняет время. Журнал содержит UUID автора, а не его
email/пароль; история коробки или задачи остаётся после удаления самого объекта.

## Демо для проверки

В профилях dev/prod DataLoader создаёт для `user1` проект «Переезд в новую квартиру»:
4 комнаты, 5 коробок во всех пяти статусах, 12 вещей (одна потерянная), 12 задач
(11 шаблонных и одна собственная, две выполнены), 6 пунктов первого дня
(два выполнены) и 18 событий. Переезд назначен на дату через 14 дней, UTC.
Пользователи `admin` и `user1` создаются с паролями из переменных окружения.
Локальный справочник шаблонов инициализируется отдельно до загрузки демоданных.

Сценарий проверки: войти как user1 → открыть проект → комнаты и коробки →
проверить статусы и статистику → задачи → чек-лист → журнал. Для чистого сценария
создать свой проект: готовый план и первый день появятся автоматически.

## Тесты и отчёты

- `mvn test` — только unit-тесты, без Docker.
- `mvn clean verify` — все тесты и отчёты JaCoCo; интеграционные используют отдельную БД в Testcontainers.
- В GitLab результаты тестов: **Build → Pipelines → pipeline → Tests**.
- JaCoCo: открой job **test → Job artifacts → Download**, распакуй архив и открой:

  - `target/site/jacoco/index.html` — общее покрытие;
  - `target/site/jacoco-unit/index.html` — unit;
  - `target/site/jacoco-integration/index.html` — интеграционные.

После локального прогона отчёты лежат по тем же путям. Подробнее — [TESTING.md](TESTING.md).
