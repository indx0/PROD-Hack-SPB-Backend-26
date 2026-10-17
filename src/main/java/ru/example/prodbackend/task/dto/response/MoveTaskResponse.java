package ru.example.prodbackend.task.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.*;
import ru.example.prodbackend.task.entity.TaskDueType;
import ru.example.prodbackend.task.entity.TaskStage;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class MoveTaskResponse {
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Уникальный идентификатор записи")
    private UUID id;
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Идентификатор проекта переезда")
    private UUID projectId;
    @Schema(types = {"string", "null"}, format = "uuid", description = "Текущая папка задачи; null для задачи без папки")
    private UUID folderId;
    @Schema(types = {"string", "null"}, format = "uuid", description = "UUID связанной вещи; null при отсутствии связи или после удаления вещи")
    private UUID linkedItemId;
    @Schema(types = {"string", "null"}, description = "Актуальное имя вещи; название самой задачи независимо от него")
    private String linkedItemName;
    @Schema(types = {"string", "null"}, format = "uuid", description = "Текущая коробка связанной вещи; null без связи")
    private UUID boxId;
    @Schema(types = {"integer", "null"}, description = "Текущий номер коробки связанной вещи; null без связи")
    private Integer boxNumber;
    @Schema(types = {"string", "null"}, format = "uuid", description = "Текущая комната коробки связанной вещи; null без связи")
    private UUID roomId;
    @Schema(types = {"string", "null"}, description = "Актуальное название комнаты связанной вещи; null без связи")
    private String roomName;
    @Schema(types = {"string", "null"}, description = "Актуальный значок комнаты связанной вещи; null без значка или связи")
    private String roomIconKey;
    @Schema(example = "Упаковать кухню", description = "Название задачи")
    private String title;
    @Schema(example = "Подготовить вещи к переезду", description = "Описание; может отсутствовать")
    private String description;
    @Schema(description = "Этап переезда: BEFORE_MOVE — до переезда, MOVING_DAY — день переезда, AFTER_MOVE — после переезда")
    private TaskStage stage;
    @Schema(example = "2026-11-01", description = "Срок выполнения: календарная дата без времени; null, если срок не задан")
    private LocalDate dueDate;
    @Schema(description = "FIXED — фиксированный срок или без срока; RELATIVE — сохранённый срок относительно переезда")
    private TaskDueType dueType;
    @Schema(description = "Смещение в календарных днях для следующего явного пересчёта; только для RELATIVE", example = "-2")
    private Integer offsetDays;
    @Schema(description = "UUID аккаунта ответственного участника; null для свободного имени или отсутствующего назначения")
    private UUID assigneeId;
    @Schema(description = "Актуальные имя и фамилия назначенного аккаунта, при отсутствии имени — email; иначе сохранённое свободное имя или null")
    private String assigneeName;
    @Schema(description = "Признак выполнения")
    private boolean completed;
    @Schema(example = "2026-11-01T09:00:00Z", description = "Дата и время выполнения в UTC; null для невыполненной записи")
    private Instant completedAt;

    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Исходный шаблон; null для собственной задачи")
    private UUID templateId;
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Запись применения личного набора; null для обычной задачи или общего шаблона")
    private UUID taskSetApplicationId;
    @Schema(example = "2026-11-01T09:00:00Z", description = "Дата и время создания в UTC")
    private Instant createdAt;
    @Schema(example = "2026-11-01T09:00:00Z", description = "Дата и время последнего изменения в UTC")
    private Instant updatedAt;
}
