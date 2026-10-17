package ru.example.prodbackend.box.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import ru.example.prodbackend.box.entity.BoxStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CorrectBoxStatusRequest {
    @NotNull
    @Schema(description = "Статус, который пользователь видел перед подтверждением", example = "DELIVERED")
    private BoxStatus expectedStatus;

    @NotNull
    @Schema(description = "Более ранний этап, на который нужно вернуть коробку", example = "PACKING")
    private BoxStatus targetStatus;

    @NotNull
    @AssertTrue(message = "Подтвердите исправление статуса и последствия для отметок распаковки")
    @Schema(description = "Обязательное явное подтверждение; допустимо только true", example = "true")
    private Boolean confirmed;
}
