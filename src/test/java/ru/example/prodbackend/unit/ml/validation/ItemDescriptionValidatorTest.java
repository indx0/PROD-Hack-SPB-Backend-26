package ru.example.prodbackend.unit.ml.validation;

import ru.example.prodbackend.ml.validation.ItemDescriptionValidator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.example.prodbackend.ml.exception.MlException;
import tools.jackson.databind.json.JsonMapper;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class ItemDescriptionValidatorTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final ItemDescriptionValidator validator = new ItemDescriptionValidator(mapper);

    @Test
    void preservesDuplicatesOrderAndPerItemFlags() {
        var result = validator.validateItems(envelope("""
                {"items":[{"item":" Кружка ","flags":["fragile"]},
                          {"item":"Кружка","flags":[]},
                          {"item":"Зарядка","flags":["first_day","carry_personally"]}]}
                """));
        assertThat(result.getItems()).extracting(item -> item.getName()).containsExactly("Кружка", "Кружка", "Зарядка");
        assertThat(result.getItems().get(0).getFlags()).extracting(flag -> flag.value()).containsExactly("fragile");
        assertThat(result.getItems().get(1).getFlags()).isEmpty();
        assertThat(result.getItems().get(2).getFlags()).extracting(flag -> flag.value())
                .containsExactly("first_day", "carry_personally");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    void acceptsListBoundaries(int size) {
        assertThat(validator.validateItems(envelope(list(size))).getItems()).hasSize(size);
    }

    @Test
    void rejectsOversizedListWithoutTruncation() {
        assertCode(list(101), "LLM_INVALID_RESPONSE", 502);
    }

    @Test
    void emptyListIsUnrecognized() {
        assertCode("{\"items\":[]}", "IMAGE_NOT_RECOGNIZED", 422);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json", "null", "[]", "{}", "{\"items\":null}",
            "{\"items\":{}}", "{\"items\":[],\"extra\":true}", "{\"other\":[]}",
            "{\"items\":[null]}", "{\"items\":[{}]}", "{\"items\":[\"Кружка\"]}",
            "{\"items\":[{\"item\":\"Кружка\",\"flags\":[],\"quantity\":2}]}"})
    void rejectsInvalidListStructure(String content) {
        assertCode(content, "LLM_INVALID_RESPONSE", 502);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"item\":\"\",\"flags\":[]}", "{\"item\":\"  \",\"flags\":[]}",
            "{\"item\":null,\"flags\":[]}", "{\"item\":2,\"flags\":[]}", "{\"item\":\"Кружка\"}",
            "{\"item\":\"Кружка\",\"flags\":null}", "{\"item\":\"Кружка\",\"flags\":[null]}",
            "{\"item\":\"Кружка\",\"flags\":[1]}", "{\"item\":\"Кружка\",\"flags\":[\"unknown\"]}",
            "{\"item\":\"Кружка\",\"flags\":[\"fragile\",\"fragile\"]}"})
    void rejectsWholeListIfOneElementIsInvalid(String item) {
        assertCode("{\"items\":[{\"item\":\"Книга\",\"flags\":[]}," + item + "]}", "LLM_INVALID_RESPONSE", 502);
    }

    @Test
    void enforcesNameLengthAndPreservesSingleItemEmptySentinel() {
        assertThat(validator.validateItems(envelope(mapper.writeValueAsString(Map.of("items",
                List.of(Map.of("item", "x".repeat(255), "flags", List.of())))))).getItems().get(0).getName()).hasSize(255);
        assertCode(mapper.writeValueAsString(Map.of("items",
                List.of(Map.of("item", "x".repeat(256), "flags", List.of())))), "LLM_INVALID_RESPONSE", 502);
        assertThatThrownBy(() -> validator.validate(envelope("{\"item\":\"\",\"flags\":[]}")))
                .isInstanceOfSatisfying(MlException.class, e -> assertThat(e.getCode()).isEqualTo("IMAGE_NOT_RECOGNIZED"));
    }

    private String list(int size) {
        return mapper.writeValueAsString(Map.of("items", Collections.nCopies(size,
                Map.of("item", "Кружка", "flags", List.of()))));
    }

    private void assertCode(String content, String code, int status) {
        assertThatThrownBy(() -> validator.validateItems(envelope(content)))
                .isInstanceOfSatisfying(MlException.class, error -> {
                    assertThat(error.getCode()).isEqualTo(code);
                    assertThat(error.getStatus().value()).isEqualTo(status);
                });
    }

    private String envelope(String content) {
        return mapper.writeValueAsString(Map.of("choices", List.of(Map.of("finish_reason", "stop",
                "message", Map.of("content", content)))));
    }
}
