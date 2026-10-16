package ru.example.prodbackend.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class PaginationRequest {

    @Min(value = 0, message = "Номер страницы не может быть отрицательным")
    @Schema(description = "Номер страницы, начиная с 0", defaultValue = "0")
    private int page = 0;

    @Min(value = 1, message = "Размер страницы должен быть не меньше 1")
    @Max(value = 100, message = "Размер страницы должен быть не больше 100")
    @Schema(description = "Количество элементов на странице", defaultValue = "20")
    private int size = 20;
}
