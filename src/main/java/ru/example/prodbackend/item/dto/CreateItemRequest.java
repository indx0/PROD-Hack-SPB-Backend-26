package ru.example.prodbackend.item.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@AllArgsConstructor
public class CreateItemRequest {

    @NotBlank(message = "name is blank")
    @Size(max = 255, message = "max size is 255")
    private String name;

    @Builder.Default
    @NotNull(message = "quantity is null")
    @Positive(message = "quantity not positive")
    @Schema(description = "Количество одинаковых предметов", defaultValue = "1", minimum = "1")
    private Integer quantity = 1;

    @Builder.Default
    @NotNull(message = "first day is null")
    @Schema(description = "Вещь нужна в первый день", defaultValue = "false")
    private Boolean firstDay = false;
}
