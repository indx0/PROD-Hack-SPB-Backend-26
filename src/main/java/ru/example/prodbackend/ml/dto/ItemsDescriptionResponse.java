package ru.example.prodbackend.ml.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemsDescriptionResponse {

    @ArraySchema(minItems = 1, maxItems = 100, arraySchema = @Schema( description = "Распознанные предметы в порядке модели; названия могут повторяться"))
    private List<ItemDescriptionResponse> items;
}
