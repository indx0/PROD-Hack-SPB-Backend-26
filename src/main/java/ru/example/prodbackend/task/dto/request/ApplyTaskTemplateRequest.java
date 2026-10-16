package ru.example.prodbackend.task.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@AllArgsConstructor
public class ApplyTaskTemplateRequest {

    @NotNull
    private UUID templateId;

    @Schema(description = "Необязательный срок выполнения", example = "2026-11-01")
    private LocalDate dueDate;

    @Size(max = 255)
    private String assigneeName;
}