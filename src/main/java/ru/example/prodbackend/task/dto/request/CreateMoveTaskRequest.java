package ru.example.prodbackend.task.dto.request;

import com.fasterxml.jackson.annotation.*;
import ru.example.prodbackend.task.entity.TaskDueType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import ru.example.prodbackend.task.entity.TaskStage;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@AllArgsConstructor
public class CreateMoveTaskRequest {

    @NotBlank(message = "title is blank")
    @Size(max = 255)
    private String title;

    @Size(max = 10000)
    private String description;

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
    @Schema(description = "FIXED — ручная дата; RELATIVE — дата переезда в UTC плюс offsetDays")
    private TaskDueType dueType;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonSetter(nulls = Nulls.FAIL)
    @Schema(description = "Смещение в днях, только для RELATIVE", example = "-2")
    private Integer offsetDays;

    @Size(max = 255)
    @Schema(description = "Необязательное имя ответственного")
    private String assigneeName;
}