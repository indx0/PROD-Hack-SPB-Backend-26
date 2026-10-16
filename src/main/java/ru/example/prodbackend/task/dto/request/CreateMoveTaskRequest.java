package ru.example.prodbackend.task.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
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
    private LocalDate dueDate;

    @Size(max = 255)
    @Schema(description = "Необязательное имя ответственного")
    private String assigneeName;
}