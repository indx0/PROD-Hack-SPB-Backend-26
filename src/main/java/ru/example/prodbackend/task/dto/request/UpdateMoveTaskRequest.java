package ru.example.prodbackend.task.dto.request;

import com.fasterxml.jackson.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ru.example.prodbackend.task.entity.TaskStage;
import ru.example.prodbackend.task.entity.TaskDueType;

import java.time.LocalDate;

@Getter
@NoArgsConstructor(onConstructor_ = @JsonCreator)
public class UpdateMoveTaskRequest {

    @Size(max = 255)
    private String title;

    @Size(max = 10000)
    private String description;

    private TaskStage stage;

    private LocalDate dueDate;

    @Schema(description = "FIXED — ручная дата; RELATIVE — срок относительно переезда. null запрещён")
    private TaskDueType dueType;
    @Schema(description = "Смещение относительно переезда; только для RELATIVE, null запрещён")
    private Integer offsetDays;

    @JsonSetter(value = "dueType", nulls = Nulls.FAIL)
    public void setDueType(TaskDueType dueType) { this.dueType = dueType; }
    @JsonSetter(value = "offsetDays", nulls = Nulls.FAIL)
    public void setOffsetDays(Integer offsetDays) { this.offsetDays = offsetDays; }

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
        return title != null || stage != null || descriptionPresent || dueDatePresent || assigneeNamePresent || dueType != null || offsetDays != null;
    }

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Неизвестное поле: " + name);
    }
}