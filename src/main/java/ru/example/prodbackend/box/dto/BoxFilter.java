package ru.example.prodbackend.box.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.example.prodbackend.box.entity.BoxStatus;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoxFilter {

    @Positive
    @Schema(description = "Точный номер коробки внутри проекта")
    private Integer number;

    @Schema(description = "Идентификатор комнаты назначения")
    private UUID roomId;

    @Schema(description = "Статус коробки")
    private BoxStatus status;

    @Schema(description = "Признак отложенного заполнения содержимого")
    private Boolean contentsDeferred;
}
