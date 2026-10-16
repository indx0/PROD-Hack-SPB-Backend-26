# Тестирование

## Запуск

Только unit-тесты — Docker и база данных не нужны:

```bash
mvn test
```

Все тесты и свежие отчёты покрытия — нужен запущенный Docker:

```bash
mvn clean verify
```

Интеграционные тесты используют отдельный PostgreSQL в Testcontainers и профиль
`test`. Рабочая база не используется; перед каждым тестом тестовые таблицы очищаются.
При первом запуске Maven скачает зависимости, а Testcontainers — Docker-образы.

Один unit-класс:

```bash
mvn -Dtest=AuthenticationServiceTest test
```

Один интеграционный класс (unit-тесты тоже выполнятся):

```bash
mvn -Dit.test=RefreshTokenIntegrationTest verify
```

После разделения Surefire/Failsafe команда `mvn test` больше не запускает
интеграционные тесты. Для полного прогона используй именно `verify`, а не
`integration-test`: так Maven проверит результат интеграционных тестов и создаст
общий отчёт покрытия.

## Отчёты

JaCoCo генерирует отдельные HTML/XML-отчёты, без исключения рабочих классов из покрытия:

- Unit: `target/site/jacoco-unit/index.html` и `jacoco.xml` в той же папке.
- Интеграционные: `target/site/jacoco-integration/index.html` и `jacoco.xml`.
- Общий: `target/site/jacoco/index.html` и `jacoco.xml`.

В HTML можно перейти к пакету, классу и строкам кода: видно, что покрыто, а что нет.
Отчёт покрытия не описывает смысл сценариев — для этого служат имена и код
тестовых методов. Для актуального полного набора отчётов запускай `mvn clean verify`.
`mvn test` обновляет только unit-отчёт; старые интеграционные/общие отчёты он не обновляет.

Результаты выполнения (не покрытие) Maven сохраняет в `target/surefire-reports`
для unit и `target/failsafe-reports` для интеграционных тестов. В GitLab они
публикуются во вкладке **Tests** пайплайна. HTML-отчёты JaCoCo можно скачать
в артефактах job **test**, распаковать и открыть соответствующий `index.html`.
