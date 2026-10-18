package ru.example.prodbackend.unit.ml.validation;

import ru.example.prodbackend.ml.validation.ItemDescriptionValidator;
import ru.example.prodbackend.ml.validation.UnpackingVerificationValidator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.example.prodbackend.ml.dto.InventoryItem;
import ru.example.prodbackend.ml.exception.MlException;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class UnpackingVerificationValidatorTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final UnpackingVerificationValidator validator = new UnpackingVerificationValidator(new ItemDescriptionValidator(mapper));
    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();
    private final List<InventoryItem> inventory = List.of(new InventoryItem(first, "Кружка"), new InventoryItem(second, "Кружка"));

    @Test
    void matchesCopiesInInventoryOrderAndPreservesUnmatchedObjects() {
        var result = validator.validate(envelope("{\"items\":[" + item(second, " Белая кружка ") + ","
                + item(null, "Тарелка") + "," + item(first, "Кружка") + "]}"), inventory);
        assertThat(result).extracting(object -> object.getItemId()).containsExactly(first, null, second);
        assertThat(result).extracting(object -> object.getPhotoDescription()).containsExactly("Белая кружка", "Тарелка", "Кружка");
    }

    @Test
    void aSingleVisibleCopyMatchesOnlyOneInventoryEntry() {
        var result = validator.validate(envelope("{\"items\":[" + item(second, "Кружка") + "]}"), inventory);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getItemId()).isEqualTo(first);
    }

    @Test
    void allowsOneHundredUnmatchedObjectsButRejectsOneHundredAndOne() {
        String object = item(null, "Предмет");
        assertThat(validator.validate(envelope("{\"items\":[" + String.join(",", Collections.nCopies(100, object)) + "]}"), inventory)).hasSize(100);
        invalid("{\"items\":[" + String.join(",", Collections.nCopies(101, object)) + "]}");
    }

    @Test
    void rejectsUnknownIdsAndRepeatedRawIdsEvenForIdenticalNames() {
        invalid("{\"items\":[" + item(UUID.randomUUID(), "Кружка") + "]}");
        invalid("{\"items\":[" + item(second, "Кружка") + "," + item(second, "Кружка") + "]}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "not json", "{\"items\":null}", "{\"items\":[],\"extra\":true}",
            "{\"items\":[{\"photoDescription\":\"Предмет\"}]}",
            "{\"items\":[{\"itemId\":null,\"photoDescription\":\" \"}]}",
            "{\"items\":[{\"itemId\":12,\"photoDescription\":\"Предмет\"}]}",
            "{\"items\":[{\"itemId\":\"bad\",\"photoDescription\":\"Предмет\"}]}",
            "{\"items\":[{\"itemId\":null,\"photoDescription\":4}]}",
            "{\"items\":[{\"itemId\":null,\"photoDescription\":\"Предмет\",\"extra\":true}]}"})
    void rejectsInvalidContent(String json) { invalid(json); }

    @Test
    void rejectsAnInvalidElementWithoutReturningPartialResult() {
        invalid("{\"items\":[" + item(first, "Кружка") + "," + item(null, "x".repeat(256)) + "]}");
        assertThat(validator.validate(envelope("{\"items\":[" + item(null, "x".repeat(255)) + "]}"), inventory)).hasSize(1);
    }

    @Test
    void emptyRefusedOrTruncatedResponsesAreHandled() {
        code(envelope("{\"items\":[]}"), "IMAGE_NOT_RECOGNIZED");
        code("{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"refusal\":\"refused\"}}]}", "IMAGE_NOT_RECOGNIZED");
        code("{\"choices\":[{\"finish_reason\":\"content_filter\"}]}", "IMAGE_NOT_RECOGNIZED");
        code("{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"{}\"}}]}", "LLM_INVALID_RESPONSE");
        code("not json", "LLM_INVALID_RESPONSE");
    }

    private String item(UUID id, String description) {
        var node = mapper.createObjectNode();
        if (id == null) node.putNull("itemId"); else node.put("itemId", id.toString());
        node.put("photoDescription", description);
        return mapper.writeValueAsString(node);
    }
    private String envelope(String content) {
        return mapper.writeValueAsString(Map.of("choices", List.of(Map.of("finish_reason", "stop", "message", Map.of("content", content)))));
    }
    private void invalid(String json) { code(envelope(json), "LLM_INVALID_RESPONSE"); }
    private void code(String body, String code) {
        assertThatThrownBy(() -> validator.validate(body, inventory)).isInstanceOfSatisfying(MlException.class,
                error -> assertThat(error.getCode()).isEqualTo(code));
    }
}
