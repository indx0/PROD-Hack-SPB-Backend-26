package ru.example.prodbackend.taskset.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record SaveTaskSetRequest(@NotBlank @Size(max = 255) String name, @Size(max = 10000) String description,
                                 @NotNull @Size(min = 1, max = 100) List<@NotNull @Valid TaskSetItemRequest> items) { }
