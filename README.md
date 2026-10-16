# ProdBackend

Макет бэкенда: Java 17, Spring Boot, PostgreSQL, JWT access/refresh.

- API: https://progr.puhlic.ru
- Swagger: https://progr.puhlic.ru/docs

## Как вносить изменения

Работаем в отдельных ветках.

```bash
git switch main
git pull --ff-only origin main
git switch -c feature/task-name
```

После изменений:

```bash
git add .
git commit -m "Add feature"
git push -u origin feature/task-name
```

1. В GitLab создай **Merge Request** из своей ветки в `main`.
2. В MR автоматически запустятся unit- и интеграционные тесты. Последующие push
   в эту же ветку снова запустят проверку. Без открытого MR feature-ветки не проверяются.
3. После успешных тестов и проверки изменений мержим MR.
4. В `main` запускается новый pipeline: **тесты → сборка Docker-образа → деплой на VPS**.

## Локальный запуск

Создай `.env` в корне проекта. Значения ниже замени своими; 

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

**Сейчас при каждом запуске приложения таблицы пересоздаются (`ddl-auto=create`),
а DataLoader заново создаёт стартовых пользователей и refresh-сессии.
Это относится и к VPS: данные между перезапусками приложения не сохраняются.**

## Тесты и отчёты

- `mvn test` — только unit-тесты, без Docker.
- `mvn clean verify` — все тесты и отчёты JaCoCo; интеграционные используют отдельную БД в Testcontainers.
- В GitLab результаты тестов: **Build → Pipelines → pipeline → Tests**.
- JaCoCo: открой job **test → Job artifacts → Download**, распакуй архив и открой:

  - `target/site/jacoco/index.html` — общее покрытие;
  - `target/site/jacoco-unit/index.html` — unit;
  - `target/site/jacoco-integration/index.html` — интеграционные.

После локального прогона отчёты лежат по тем же путям. Подробнее — [TESTING.md](TESTING.md).
