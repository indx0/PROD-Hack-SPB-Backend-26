package ru.example.prodbackend.taskset.dto;

import jakarta.validation.constraints.*;
import ru.example.prodbackend.task.entity.TaskStage;

public record TaskSetItemRequest(@NotBlank @Size(max = 255) String title, @Size(max = 10000) String description,
                                 @NotNull TaskStage stage, @NotNull Integer offsetDays) { }
