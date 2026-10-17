package ru.example.prodbackend.task.dto.request;

import com.fasterxml.jackson.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ru.example.prodbackend.task.entity.TaskDueType;
import ru.example.prodbackend.task.entity.TaskStage;

@Getter
@NoArgsConstructor(onConstructor_ = @JsonCreator)
public class UpdateMoveTaskRequest {

    @Schema(types = {"string", "null"}, format = "uuid", description = "Папка этого переезда: отсутствие сохраняет значение, null выносит задачу из папки. Чужая или отсутствующая папка — 404 TASK_FOLDER_NOT_FOUND; весь PATCH откатывается")
    private UUID folderId;

    @Schema(types = {"string", "null"}, format = "uuid", description = "Связанная вещь этого переезда: отсутствие сохраняет связь, null снимает. Чужая или отсутствующая вещь — 404; весь PATCH откатывается. Не меняет item.firstDay")
    private UUID linkedItemId;

    @JsonIgnore
    @Schema(hidden = true)
    private boolean folderIdPresent;

    @JsonIgnore
    @Schema(hidden = true)
    private boolean linkedItemIdPresent;

    @JsonSetter(value = "folderId", nulls = Nulls.SET)
    public void setFolderId(UUID folderId) {
        this.folderId = folderId;
        this.folderIdPresent = true;
    }

    @JsonSetter(value = "linkedItemId", nulls = Nulls.SET)
    public void setLinkedItemId(UUID linkedItemId) {
        this.linkedItemId = linkedItemId;
        this.linkedItemIdPresent = true;
    }

    @Schema(example = "Упаковать кухню", description = "Новое название задачи; отсутствие сохраняет значение, null запрещён")
    @Size(max = 255)
    private String title;

    @Schema(example = "Подготовить вещи к переезду", description = "Новое описание; отсутствие сохраняет значение, null очищает")
    @Size(max = 10000)
    private String description;
    @Schema(description = "Новый этап переезда; отсутствие сохраняет значение, null запрещён")
    private TaskStage stage;
    @Schema(example = "2026-11-01", description = "Новая фиксированная дата без времени; отсутствие сохраняет срок, null очищает")
    private LocalDate dueDate;

    @Schema(description = "FIXED — ручная дата; RELATIVE — срок относительно переезда. null запрещён")
    private TaskDueType dueType;
    @Schema(example = "-2", description = "Смещение относительно переезда; только для RELATIVE, null запрещён")
    private Integer offsetDays;

    @JsonSetter(value = "dueType", nulls = Nulls.FAIL)
    public void setDueType(TaskDueType dueType) { this.dueType = dueType; }
    @JsonSetter(value = "offsetDays", nulls = Nulls.FAIL)
    public void setOffsetDays(Integer offsetDays) { this.offsetDays = offsetDays; }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "UUID аккаунта участника этого переезда, включая создателя. Имеет приоритет над assigneeName; явный null снимает назначение. Отсутствие в PATCH сохраняет назначение")
    private UUID assigneeId;

    @JsonIgnore
    @Schema(hidden = true)
    private boolean assigneeIdPresent;

    @JsonSetter(value = "assigneeId", nulls = Nulls.SET)
    public void setAssigneeId(UUID assigneeId) {
        this.assigneeId = assigneeId;
        this.assigneeIdPresent = true;
    }

    @Schema(description = "Свободное имя ответственного: без assigneeId снимает связь с аккаунтом; null очищает назначение, отсутствие сохраняет")
    @Size(max = 255)
    private String assigneeName;

    @JsonIgnore
    @Schema(hidden = true)
    private boolean descriptionPresent;

    @JsonIgnore
    @Schema(hidden = true)
    private boolean dueDatePresent;

    @JsonIgnore
    @Schema(hidden = true)
    private boolean assigneeNamePresent;

    @JsonSetter(value = "title", nulls = Nulls.FAIL)
    public void setTitle(String title) {
        this.title = title;
    }

    @JsonSetter(value = "stage", nulls = Nulls.FAIL)
    public void setStage(TaskStage stage) {
        this.stage = stage;
    }

    @JsonSetter(value = "description", nulls = Nulls.SET)
    public void setDescription(String description) {
        this.description = description;
        this.descriptionPresent = true;
    }

    @JsonSetter(value = "dueDate", nulls = Nulls.SET)
    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
        this.dueDatePresent = true;
    }

    @JsonSetter(value = "assigneeName", nulls = Nulls.SET)
    public void setAssigneeName(String assigneeName) {
        this.assigneeName = assigneeName;
        this.assigneeNamePresent = true;
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "Укажите хотя бы одно поле для изменения")
    public boolean isUpdatePresent() {
        return title != null || stage != null || descriptionPresent || dueDatePresent || assigneeNamePresent || assigneeIdPresent || dueType != null || offsetDays != null || folderIdPresent || linkedItemIdPresent;
    }

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Неизвестное поле: " + name);
    }
}
