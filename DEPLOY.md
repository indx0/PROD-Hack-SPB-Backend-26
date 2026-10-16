# Деплой на Spirit

ВМ: `hack-lead-01-ru3-vm1-3a8de1`, публичный IP `45.12.229.245`.
Домен: `hack-lead-01-ru3-vm1-3a8de1.vmaas-spirit.t-education.com`.
Ручной доступ — web-терминал Teleport.

## Однократная подготовка ВМ

Docker Engine и Compose уже установлены. На ВМ не нужны Maven, Java или GitLab Runner.

1. В web-терминале создай каталог и конфигурацию приложения:

   ```bash
   sudo install -d -m 0750 /opt/prod-backend
   sudo touch /opt/prod-backend/.env
   sudo chmod 600 /opt/prod-backend/.env
   sudo nano /opt/prod-backend/.env
   ```

   Содержимое (замени значения):

   ```dotenv
   DB_USERNAME=movermate
   DB_PASSWORD=replace_with_random_password
   SECRET_KEY=replace_with_base64_key
   ADMIN_PASSWORD=replace_with_admin_password
   USER1_PASSWORD=replace_with_user1_password
   ```

   JWT-ключ: `openssl rand -base64 32`. Пароли: `openssl rand -hex 24`.
   `DB_URL` и `APP_IMAGE` здесь не нужны: URL задан в Compose, образ передаёт CI.
   Не публикуй `.env` в репозитории или логах.

2. Установи reverse proxy и Certbot:

   ```bash
   sudo apt-get update
   sudo apt-get install -y nginx certbot python3-certbot-nginx
   sudo nano /etc/nginx/sites-available/movermate
   ```

   Скопируй в файл содержимое `deploy/nginx.conf`, затем:

   ```bash
   sudo ln -s /etc/nginx/sites-available/movermate /etc/nginx/sites-enabled/movermate
   sudo nginx -t
   sudo systemctl enable --now nginx
   sudo systemctl reload nginx
   sudo certbot --nginx -d hack-lead-01-ru3-vm1-3a8de1.vmaas-spirit.t-education.com --redirect
   ```

   Выпуск сертификата требует доступности домена снаружи по порту 80 и разрешения
   организаторов на использование этого имени. Если ACME вернёт ошибку DNS,
   CAA или rate limit — согласуй домен с поддержкой, не повторяй запросы вслепую.
   До первого деплоя ответ Nginx `502` ожидаем: приложение ещё не запущено.

3. После выпуска проверь таймер продления:

   ```bash
   sudo systemctl list-timers --all certbot.timer
   ```

## GitLab / Spirit

- Проект: `https://gitlab.t-education.com/hack-led-01/backend`.
- Основная ветка: `master`; изменения попадают в неё через MR.
- Сервисный аккаунт уже есть: `sa-hack-led-01-spirit-docker-ci`.
- В Spirit существует CI-переменная `SPIRIT_DOCKER_SA_KEY` типа **Файл**.
  Её значение не копируют в репозиторий. Для защищённой переменной `master`
  должна оставаться защищённой веткой; область видимости должна охватывать
  и сборку без environment, и деплой в `production`.

Pipeline:

1. MR — unit + интеграционные тесты, JUnit и JaCoCo. PostgreSQL — одноразовый
   сервис job; privileged Runner и Docker socket для тестов не требуются.
2. `master` — те же тесты, затем `.dp_build` из `pipelines/pipelines`: Kaniko
   собирает Dockerfile с JAR из артефактов и публикует образ с полным SHA коммита.
3. Деплой — `.teleport_auth`, образ `cicd-images/tsh-18`. CI передаёт Compose,
   скрипт деплоя и краткоживущую Registry-авторизацию на ВМ, запускает
   `scripts/deploy.sh`. Постоянный ключ сервис-аккаунта на ВМ не сохраняется.
4. После готовности контейнеров CI проверяет публичные HTTPS health и OpenAPI.

В `.gitlab-ci.yml` намеренно нет тега старого Runner `prod-backend`.
Если job без тегов остаётся `Pending/stuck`, организаторы должны подтвердить
теги и доступность предоставленного Runner. Изменения CI проходят тот же MR.

Для проверки CI в своей ветке без MR можно выбрать **New pipeline** и нужную ветку.
Сборка образа и деплой выполняются только для основной ветки.

## Диагностика

В web-терминале ВМ:

```bash
cd /opt/prod-backend
sudo docker compose --env-file .env --env-file .image.env -f compose.prod.yaml ps
sudo docker compose --env-file .env --env-file .image.env -f compose.prod.yaml logs --tail 100 app
curl -fsS http://127.0.0.1:8080/actuator/health/readiness
```

С любого компьютера:

```bash
curl -fsS https://hack-lead-01-ru3-vm1-3a8de1.vmaas-spirit.t-education.com/actuator/health/readiness
```

Swagger: `https://hack-lead-01-ru3-vm1-3a8de1.vmaas-spirit.t-education.com/docs`.

Сейчас сохраняется прежний режим `ddl-auto=create`: при запуске приложения
таблицы пересоздаются, затем DataLoader создаёт `admin` и `user1`.
PostgreSQL volume не отменяет это поведение. Перед использованием постоянных
демонстрационных данных отдельно измените режим схемы и повторный запуск DataLoader.
