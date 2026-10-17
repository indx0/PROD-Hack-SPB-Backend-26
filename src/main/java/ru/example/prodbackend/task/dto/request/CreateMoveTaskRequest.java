package ru.example.prodbackend.task.dto.request;

import com.fasterxml.jackson.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import lombok.*;
import ru.example.prodbackend.task.entity.TaskDueType;
import ru.example.prodbackend.task.entity.TaskStage;

@Data
@Builder
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@AllArgsConstructor
public class CreateMoveTaskRequest {

    @Schema(types = {"string", "null"}, format = "uuid", description = "Папка этого переезда. Отсутствие или null создаёт задачу без папки; чужая или отсутствующая папка — 404 TASK_FOLDER_NOT_FOUND")
    private UUID folderId;

    @Schema(types = {"string", "null"}, format = "uuid", description = "Связанная вещь этого переезда. Отсутствие или null означает отсутствие связи; чужая или отсутствующая вещь — 404. Не изменяет item.firstDay или упаковку")
    private UUID linkedItemId;

    @Schema(example = "Упаковать кухню", description = "Название задачи")
    @NotBlank(message = "title is blank")
    @Size(max = 255)
    private String title;

    @Schema(example = "Подготовить вещи к переезду", description = "Описание; может отсутствовать")
    @Size(max = 10000)
    private String description;

    @Schema(description = "Этап переезда: BEFORE_MOVE — до переезда, MOVING_DAY — день переезда, AFTER_MOVE — после переезда")
    @NotNull(message = "stage is null")
    private TaskStage stage;

    @Schema(description = "Срок выполнения, без времени", example = "2026-11-01")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private LocalDate dueDate;

    @JsonIgnore
    @Schema(hidden = true)
    private boolean dueDatePresent;

    @JsonSetter(value = "dueDate", nulls = Nulls.SET)
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; this.dueDatePresent = true; }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonSetter(nulls = Nulls.FAIL)
    @Schema(defaultValue = "FIXED", description = "FIXED — ручная дата; RELATIVE — дата переезда в UTC плюс offsetDays")
    private TaskDueType dueType;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonSetter(nulls = Nulls.FAIL)
    @Schema(description = "Смещение в днях, только для RELATIVE", example = "-2")
    private Integer offsetDays;

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

    @Size(max = 255)
    @Schema(description = "Свободное имя ответственного, если assigneeId не передан; пробелы по краям удаляются")
    private String assigneeName;
}
