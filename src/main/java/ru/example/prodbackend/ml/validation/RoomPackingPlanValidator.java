package ru.example.prodbackend.ml.validation;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import ru.example.prodbackend.ml.dto.RoomPackingPlanResponse;
import ru.example.prodbackend.ml.dto.RoomPackingPlanResponse.*;
import ru.example.prodbackend.ml.exception.MlException;
import tools.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RoomPackingPlanValidator {
    public static final int MAX_ENTRIES = 100;
    public static final int MAX_NAME = 255;
    public static final int MAX_EXPLANATION = 1000;
    private final ItemDescriptionValidator envelopes;

    public RoomPackingPlanResponse validate(String body) {
        var result = envelopes.content(body, this::notGenerated);
        fields(result, "boxGroups", "packingOrder", "declutteringSuggestions", "assumptions");
        for (String name : List.of("boxGroups", "packingOrder", "declutteringSuggestions", "assumptions")) {
            array(result.path(name));
        }
        if (result.path("boxGroups").isEmpty()) {
            if (!result.path("packingOrder").isEmpty() || !result.path("declutteringSuggestions").isEmpty()
                    || !result.path("assumptions").isEmpty()) throw invalid();
            throw notGenerated();
        }
        var groups = new ArrayList<BoxGroup>();
        int total = 0;
        for (var group : result.path("boxGroups")) {
            fields(group, "label", "count", "items", "packingAdvice");
            var count = group.path("count");
            if (!count.isIntegralNumber() || !count.canConvertToInt() || count.asInt() < 1
                    || count.asInt() > MAX_ENTRIES) throw invalid();
            total += count.asInt();
            if (total > MAX_ENTRIES) throw invalid();
            groups.add(new BoxGroup(string(group.path("label"), MAX_NAME), count.asInt(),
                    strings(group.path("items"), MAX_NAME), string(group.path("packingAdvice"), MAX_EXPLANATION)));
        }
        var suggestions = new ArrayList<DeclutteringSuggestion>();
        for (var suggestion : result.path("declutteringSuggestions")) {
            fields(suggestion, "item", "action", "reason");
            String value = string(suggestion.path("action"), MAX_NAME);
            Action action = Arrays.stream(Action.values()).filter(candidate -> candidate.value().equals(value))
                    .findFirst().orElseThrow(this::invalid);
            suggestions.add(new DeclutteringSuggestion(string(suggestion.path("item"), MAX_NAME), action,
                    string(suggestion.path("reason"), MAX_EXPLANATION)));
        }
        return new RoomPackingPlanResponse(total, List.copyOf(groups),
                strings(result.path("packingOrder"), MAX_EXPLANATION), List.copyOf(suggestions),
                strings(result.path("assumptions"), MAX_EXPLANATION));
    }

    private void fields(JsonNode object, String... names) {
        if (!object.isObject() || object.size() != names.length) throw invalid();
        for (String name : names) if (!object.has(name)) throw invalid();
    }
    private void array(JsonNode value) {
        if (!value.isArray() || value.size() > MAX_ENTRIES) throw invalid();
    }
    private List<String> strings(JsonNode value, int max) {
        array(value);
        if (value.isEmpty()) throw invalid();
        var values = new ArrayList<String>();
        for (var entry : value) values.add(string(entry, max));
        return List.copyOf(values);
    }
    private String string(JsonNode value, int max) {
        if (!value.isString()) throw invalid();
        String text = value.asText().strip();
        if (text.isEmpty() || text.length() > max) throw invalid();
        return text;
    }
    private MlException notGenerated() {
        return new MlException(HttpStatus.UNPROCESSABLE_CONTENT, "ROOM_PLAN_NOT_GENERATED",
                "Не удалось составить план; уточните описание комнаты или фотографию");
    }
    private MlException invalid() {
        return new MlException(HttpStatus.BAD_GATEWAY, "LLM_INVALID_RESPONSE", "Модель вернула некорректный план упаковки");
    }
}
