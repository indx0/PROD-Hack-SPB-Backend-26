package ru.example.prodbackend.taskset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record SaveTaskSetRequest(
        @Schema(example = "Подготовка кухни", description = "Название личного набора задач")
@NotBlank @Size(max = 255) String name,
        @Schema(example = "Подготовить вещи к переезду", description = "Описание до 10000 символов; отсутствие или null очищает значение")
@Size(max = 10000) String description,
        @Schema(description = "От 1 до 100 задач; порядок списка задаёт позиции, начиная с 0")
@NotNull @Size(min = 1, max = 100) List<@NotNull @Valid TaskSetItemRequest> items) { }
