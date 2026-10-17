package ru.example.prodbackend.ml.validation;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import ru.example.prodbackend.item.entity.ItemFlag;
import ru.example.prodbackend.ml.dto.ItemDescriptionResponse;
import ru.example.prodbackend.ml.dto.ItemsDescriptionResponse;
import ru.example.prodbackend.ml.exception.MlException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.ArrayList;
import java.util.HashSet;

@Component
@RequiredArgsConstructor
public class ItemDescriptionValidator {
    public static final int MAX_ITEMS = 100;
    private final JsonMapper mapper;

    public ItemDescriptionResponse validate(String body) {
        return validateItem(content(body), true);
    }

    public ItemsDescriptionResponse validateItems(String body) {
        JsonNode result = content(body);
        if (!result.isObject() || result.size() != 1 || !result.path("items").isArray()
                || result.path("items").size() > MAX_ITEMS) throw invalid();
        if (result.path("items").isEmpty()) throw unrecognized();
        var items = new ArrayList<ItemDescriptionResponse>();
        for (JsonNode item : result.path("items")) items.add(validateItem(item, false));
        return new ItemsDescriptionResponse(java.util.List.copyOf(items));
    }

    JsonNode content(String body) {
        JsonNode envelope = parse(body);
        JsonNode choice = envelope.path("choices").path(0);
        JsonNode message = choice.path("message");
        if ((!message.path("refusal").isMissingNode() && !message.path("refusal").isNull()
                && !message.path("refusal").asText().isEmpty())
                || "content_filter".equals(choice.path("finish_reason").asText())) throw unrecognized();
        if (!"stop".equals(choice.path("finish_reason").asText())
                || !message.path("content").isString()) throw invalid();
        return parse(message.path("content").asText());
    }

    private ItemDescriptionResponse validateItem(JsonNode result, boolean single) {
        if (!result.isObject() || result.size() != 2 || !result.path("item").isString()
                || !result.path("flags").isArray()) throw invalid();
        String name = result.path("item").asText().strip();
        if (name.length() > 255) throw invalid();
        var flags = new ArrayList<ItemFlag>();
        for (JsonNode node : result.path("flags")) {
            if (!node.isString()) throw invalid();
            ItemFlag flag = java.util.Arrays.stream(ItemFlag.values())
                    .filter(value -> value.value().equals(node.asText())).findFirst().orElseThrow(this::invalid);
            flags.add(flag);
        }
        if (new HashSet<>(flags).size() != flags.size()) throw invalid();
        if (name.isEmpty()) throw single ? unrecognized() : invalid();
        return new ItemDescriptionResponse(name, java.util.List.copyOf(flags));
    }

    private JsonNode parse(String json) {
        try {
            JsonNode value = mapper.readTree(json);
            if (value == null) throw invalid();
            return value;
        } catch (MlException e) { throw e; }
        catch (RuntimeException e) { throw invalid(); }
    }

    private MlException invalid() {
        return new MlException(HttpStatus.BAD_GATEWAY, "LLM_INVALID_RESPONSE", "Модель вернула некорректный ответ");
    }
    private MlException unrecognized() {
        return new MlException(HttpStatus.UNPROCESSABLE_CONTENT, "IMAGE_NOT_RECOGNIZED",
                "Не удалось распознать предметы на фотографии");
    }
}
