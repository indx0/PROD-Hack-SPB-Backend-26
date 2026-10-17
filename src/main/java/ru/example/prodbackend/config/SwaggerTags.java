package ru.example.prodbackend.config;

import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;

/** Единый каталог групп OpenAPI: названия используются в контроллерах, описания — в SwaggerConfig. */
public final class SwaggerTags {
    private SwaggerTags() { }

    public static final String AUTH = "Авторизация";
    public static final String USER = "Профиль пользователя";
    public static final String PROJECTS = "Проекты переезда";
    public static final String PARTICIPANTS = "Участники переезда";
    public static final String ROOMS = "Комнаты";
    public static final String BOXES = "Коробки";
    public static final String ITEMS = "Вещи";
    public static final String TASKS = "Задачи переезда";
    public static final String TASK_FOLDERS = "Папки задач";
    public static final String TEMPLATES = "Готовые задачи";
    public static final String TASK_SETS = "Личные наборы задач";
    public static final String CHECKLIST = "Чек-лист первого дня";
    public static final String EVENTS = "Журнал проекта";
    public static final String PROGRESS = "Прогресс переезда";
    public static final String PHOTO = "Опись и проверка по фото";
    public static final String PACKING = "Планирование упаковки";
    public static final String DEMO = "Диагностика";

    public static List<Tag> all() {
        return List.of(
                new Tag().name(AUTH).description("Регистрация, вход, обновление токенов и завершение сессий."),
                new Tag().name(USER).description("Просмотр и изменение профиля, смена пароля текущего пользователя."),
                new Tag().name(PROJECTS).description("Создание и ведение переездов. Архивные проекты доступны для чтения; изменения требуют восстановления."),
                new Tag().name(PARTICIPANTS).description("Совместный доступ существующих пользователей с равными правами, без приглашений и подтверждений."),
                new Tag().name(ROOMS).description("Комнаты назначения и их значки в проекте переезда."),
                new Tag().name(BOXES).description("Коробки проекта, их содержимое, особенности и последовательность статусов упаковки и доставки."),
                new Tag().name(ITEMS).description("Содержимое коробок, отметки упаковки и распаковки, перенос и поиск вещей."),
                new Tag().name(TASKS).description("Задачи проекта, сроки и выполнение; применение готовых задач."),
                new Tag().name(TASK_FOLDERS).description("Цветные папки обычных задач переезда. Равные права участников; архив доступен только для чтения."),
                new Tag().name(TEMPLATES).description("Общие образцы и личные готовые задачи аккаунта. Сохранение в каталог не добавляет задачу в переезд."),
                new Tag().name(TASK_SETS).description("Личный каталог наборов и их применение к переездам. Каталог доступен только владельцу аккаунта."),
                new Tag().name(CHECKLIST).description("Пункты первого дня после переезда и их связи с вещами проекта."),
                new Tag().name(EVENTS).description("История действий участников проекта. События сохраняются после удаления объектов."),
                new Tag().name(PROGRESS).description("Сводка по коробкам, комнатам и выполнению задач проекта."),
                new Tag().name(PHOTO).description("Распознавание предметов и рекомендательная сверка с описью. Фотографии обрабатываются внешним LLM Proxy."),
                new Tag().name(PACKING).description("Рекомендации по упаковке комнаты по тексту и фотографии, без сохранения и создания коробок."),
                new Tag().name(DEMO).description("Демонстрационные запросы для проверки доступности API и авторизации.")
        );
    }
}
