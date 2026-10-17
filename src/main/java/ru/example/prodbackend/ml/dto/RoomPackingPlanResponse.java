package ru.example.prodbackend.ml.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Рекомендации по упаковке; данные не сохраняются и коробки не создаются")
public record RoomPackingPlanResponse(
        @Schema(description = "Приблизительное количество коробок, сумма count всех групп", minimum = "1", maximum = "100")
        int boxCount,
        @ArraySchema(arraySchema = @Schema(description = "Рекомендуемые группы коробок"), minItems = 1, maxItems = 100) List<BoxGroup> boxGroups,
        @ArraySchema(minItems = 1, maxItems = 100, schema = @Schema(minLength = 1, maxLength = 1000),
                arraySchema = @Schema(description = "Действия в порядке выполнения")) List<String> packingOrder,
        @ArraySchema(maxItems = 100, arraySchema = @Schema(description = "Кандидаты на отдачу/утилизацию, требующие проверки пользователем"))
        List<DeclutteringSuggestion> declutteringSuggestions,
        @ArraySchema(minItems = 1, maxItems = 100, schema = @Schema(minLength = 1, maxLength = 1000),
                arraySchema = @Schema(description = "Допущения оценки, включая типовой размер коробок и ориентир по загрузке"))
        List<String> assumptions) {
    public record BoxGroup(
        @Schema(example = "Посуда", description = "Название группы коробок", minLength = 1, maxLength = 255) String label,
        @Schema(example = "2", description = "Количество коробок в группе", minimum = "1", maximum = "100") int count,
        @ArraySchema(arraySchema = @Schema(description = "Предметы для упаковки в коробки группы"), minItems = 1, maxItems = 100, schema = @Schema(minLength = 1, maxLength = 255)) List<String> items,
        @Schema(example = "Оберните каждый предмет бумагой", description = "Рекомендации по упаковке группы", minLength = 1, maxLength = 1000) String packingAdvice) { }
    public record DeclutteringSuggestion(
        @Schema(description = "Предмет, предложенный для отдачи или утилизации", minLength = 1, maxLength = 255) String item,
        @Schema(description = "Предлагаемое действие: donate — отдать, discard — утилизировать")
        Action action,
        @Schema(description = "Причина рекомендации", minLength = 1, maxLength = 1000) String reason) { }
    public enum Action {
        DONATE("donate"), DISCARD("discard");
        private final String value;
        Action(String value) { this.value = value; }
        @JsonValue public String value() { return value; }
    }
}
