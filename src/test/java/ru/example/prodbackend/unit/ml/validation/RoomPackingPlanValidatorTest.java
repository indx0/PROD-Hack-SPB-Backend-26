package ru.example.prodbackend.unit.ml.validation;

import ru.example.prodbackend.ml.validation.ItemDescriptionValidator;
import ru.example.prodbackend.ml.validation.RoomPackingPlanValidator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.example.prodbackend.ml.exception.MlException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.*;
import static ru.example.prodbackend.support.fixtures.RoomPackingPlanFixtures.*;

class RoomPackingPlanValidatorTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final RoomPackingPlanValidator validator = new RoomPackingPlanValidator(new ItemDescriptionValidator(mapper));

    @Test
    void sumsGroupsAndPreservesOrderAndActions() {
        var result = validator.validate(envelope(PLAN));
        assertThat(result).isEqualTo(response());
        var plan = plan();
        ((ObjectNode)plan.path("declutteringSuggestions").path(0)).put("action", "discard");
        assertThat(mapper.writeValueAsString(validator.validate(envelope(plan.toString())))).contains("\"action\":\"discard\"");
        assertThatThrownBy(() -> result.boxGroups().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void supportsEmptySuggestionsAndTrimsStrings() {
        var plan = plan();
        ((ArrayNode)plan.path("declutteringSuggestions")).removeAll();
        ((ObjectNode)plan.path("boxGroups").path(0)).put("label", " Посуда ");
        var result = validator.validate(envelope(plan.toString()));
        assertThat(result.declutteringSuggestions()).isEmpty();
        assertThat(result.boxGroups().get(0).label()).isEqualTo("Посуда");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.0", "1.5", "101", "2147483648", "null", "\"1\"", "true"})
    void rejectsNonPositiveFractionalOrUnboundedCounts(String count) {
        invalid(plan -> ((ObjectNode)plan.path("boxGroups").path(0)).set("count", mapper.readTree(count)));
    }

    @Test
    void rejectsAggregateAboveOneHundredAndAllowsExactlyOneHundred() {
        invalid(plan -> ((ObjectNode)plan.path("boxGroups").path(0)).put("count", 100));
        var plan = plan();
        ((ObjectNode)plan.path("boxGroups").path(0)).put("count", 99);
        assertThat(validator.validate(envelope(plan.toString())).boxCount()).isEqualTo(100);
    }

    @Test
    void rejectsMissingExtraAndWrongFieldsAtEveryLevel() {
        invalid(plan -> plan.put("extra", true));
        invalid(plan -> plan.remove("assumptions"));
        invalid(plan -> plan.put("boxCount", 3));
        invalid(plan -> plan.put("packingOrder", "not an array"));
        invalid(plan -> ((ObjectNode)plan.path("boxGroups").path(0)).put("extra", 1));
        invalid(plan -> ((ObjectNode)plan.path("boxGroups").path(0)).remove("items"));
        invalid(plan -> ((ObjectNode)plan.path("declutteringSuggestions").path(0)).put("extra", 1));
        invalid(plan -> ((ObjectNode)plan.path("declutteringSuggestions").path(0)).put("action", "sell"));
        invalid(plan -> ((ObjectNode)plan.path("declutteringSuggestions").path(0)).remove("reason"));
        code(envelope("null"), "LLM_INVALID_RESPONSE");
        code(envelope("not json"), "LLM_INVALID_RESPONSE");
    }

    @Test
    void validatesStringBoundariesAndAllRequiredLists() {
        invalid(plan -> ((ObjectNode)plan.path("boxGroups").path(0)).put("label", " "));
        invalid(plan -> ((ObjectNode)plan.path("boxGroups").path(0)).put("label", "x".repeat(256)));
        invalid(plan -> ((ObjectNode)plan.path("boxGroups").path(0)).put("packingAdvice", "x".repeat(1001)));
        invalid(plan -> ((ObjectNode)plan.path("declutteringSuggestions").path(0)).put("item", "x".repeat(256)));
        invalid(plan -> ((ObjectNode)plan.path("declutteringSuggestions").path(0)).put("reason", "x".repeat(1001)));
        invalid(plan -> ((ArrayNode)plan.path("boxGroups").path(0).path("items")).add("x".repeat(256)));
        invalid(plan -> ((ArrayNode)plan.path("packingOrder")).add("x".repeat(1001)));
        invalid(plan -> ((ArrayNode)plan.path("assumptions")).add("x".repeat(1001)));
        invalid(plan -> ((ArrayNode)plan.path("packingOrder")).add(12));
        for (String field : List.of("packingOrder", "assumptions")) invalid(plan -> ((ArrayNode)plan.path(field)).removeAll());
        invalid(plan -> ((ArrayNode)plan.path("boxGroups").path(0).path("items")).removeAll());
        var plan = plan();
        ((ObjectNode)plan.path("boxGroups").path(0)).put("label", "x".repeat(255)).put("packingAdvice", "x".repeat(1000));
        assertThat(validator.validate(envelope(plan.toString())).boxCount()).isEqualTo(3);
    }

    @Test
    void enforcesEveryArrayLimit() {
        for (String field : List.of("boxGroups", "packingOrder", "declutteringSuggestions", "assumptions")) {
            invalid(plan -> {
                var array = (ArrayNode)plan.path(field);
                var entry = array.path(0).deepCopy();
                while (array.size() <= 100) array.add(entry);
            });
        }
        invalid(plan -> {
            var array = (ArrayNode)plan.path("boxGroups").path(0).path("items");
            while (array.size() <= 100) array.add("Предмет");
        });
    }

    @Test
    void mapsOnlyRefusalAndEmptyPlanToNotGenerated() {
        code(envelope(NO_PLAN), "ROOM_PLAN_NOT_GENERATED");
        code("{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"refusal\":\"refused\"}}]}", "ROOM_PLAN_NOT_GENERATED");
        code("{\"choices\":[{\"finish_reason\":\"content_filter\"}]}", "ROOM_PLAN_NOT_GENERATED");
        invalid(plan -> ((ArrayNode)plan.path("boxGroups")).removeAll());
        code("not json", "LLM_INVALID_RESPONSE");
        code("{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"{}\"}}]}", "LLM_INVALID_RESPONSE");
    }

    private ObjectNode plan() { return (ObjectNode)mapper.readTree(PLAN); }
    private String envelope(String json) {
        return mapper.writeValueAsString(Map.of("choices", List.of(Map.of("finish_reason", "stop", "message", Map.of("content", json)))));
    }
    private void invalid(Consumer<ObjectNode> change) {
        var plan = plan();
        change.accept(plan);
        code(envelope(plan.toString()), "LLM_INVALID_RESPONSE");
    }
    private void code(String body, String expected) {
        assertThatThrownBy(() -> validator.validate(body)).isInstanceOfSatisfying(MlException.class,
                e -> assertThat(e.getCode()).isEqualTo(expected));
    }
}
