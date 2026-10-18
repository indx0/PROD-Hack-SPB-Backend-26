package ru.example.prodbackend.ml.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomPackingPlanRequest {

    @Schema(description = "Описание комнаты и пожелания; до 4000 символов после удаления крайних пробелов", maxLength = 4000, example = "Кухня: посуда, чайник, 20 книг; чайник нужен в первый день")
    private String description;
}
