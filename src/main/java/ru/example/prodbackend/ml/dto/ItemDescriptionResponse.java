package ru.example.prodbackend.ml.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.example.prodbackend.item.entity.ItemFlag;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemDescriptionResponse {

    @Schema(description = "Предложенное название распознанного предмета", example = "Белая керамическая кружка", maxLength = 255)
    private String name;

    @Schema(description = "Метки вещи: fragile — хрупкое, first_day — первый день, carry_personally — везти с собой")
    private List<ItemFlag> flags;
}
