package ru.example.prodbackend.box.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.example.prodbackend.box.entity.BoxStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoxFilter {

    @Positive
    @Schema(example = "1", description = "Точный номер коробки внутри проекта")
    private Integer number;

    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Идентификатор комнаты назначения")
    private UUID roomId;

    @Schema(description = "Статус коробки")
    private BoxStatus status;

    @Schema(description = "Признак отложенного заполнения содержимого")
    private Boolean contentsDeferred;

    @Schema(description = "Точное соответствие признаку хрупкости; без параметра не фильтруется")
    private Boolean fragile;
    @Schema(description = "Точное соответствие признаку первого дня; без параметра не фильтруется")
    private Boolean firstDay;
    @Schema(description = "Точное соответствие признаку «везу с собой»; без параметра не фильтруется")
    private Boolean carryWithMe;
}
