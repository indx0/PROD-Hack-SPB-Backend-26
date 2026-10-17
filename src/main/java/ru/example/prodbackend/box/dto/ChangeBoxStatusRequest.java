package ru.example.prodbackend.box.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.example.prodbackend.box.entity.BoxStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeBoxStatusRequest {

    @Schema(description = "Текущий статус коробки")
    @NotNull
    private BoxStatus status;
}