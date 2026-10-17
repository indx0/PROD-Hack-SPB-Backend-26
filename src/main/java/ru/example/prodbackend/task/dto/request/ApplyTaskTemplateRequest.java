package ru.example.prodbackend.task.dto.request;

import com.fasterxml.jackson.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import lombok.*;
import ru.example.prodbackend.task.entity.TaskDueType;

@Data
@Builder
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@AllArgsConstructor
public class ApplyTaskTemplateRequest {

    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Идентификатор готовой задачи")
    @NotNull
    private UUID templateId;

    @Schema(description = "Необязательный срок выполнения", example = "2026-11-01")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private LocalDate dueDate;

    @JsonIgnore
    @Schema(hidden = true)
    private boolean dueDatePresent;

    @JsonSetter(value = "dueDate", nulls = Nulls.SET)
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; this.dueDatePresent = true; }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonSetter(nulls = Nulls.FAIL)
    @Schema(description = "FIXED — ручная дата; RELATIVE — дата переезда в UTC плюс offsetDays")
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

    @Schema(description = "Свободное имя ответственного, если assigneeId не передан; null снимает назначение")
    @Size(max = 255)
    private String assigneeName;
}