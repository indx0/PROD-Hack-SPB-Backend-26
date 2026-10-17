package ru.example.prodbackend.unit.item.dto;

import ru.example.prodbackend.item.dto.CreateItemRequest;
import ru.example.prodbackend.item.dto.UpdateItemRequest;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class ItemFlagsDtoTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void omittedFlagsDefaultToEmpty() {
        assertThat(mapper.readValue("{\"name\":\"Кружка\"}", CreateItemRequest.class).getFlags()).isEmpty();
    }

    @Test
    void nullAndUnknownFlagsAreRejected() {
        for (String flags : new String[]{"null", "[\"unknown\"]", "[0]", "[\"FRAGILE\"]"}) {
            assertThatThrownBy(() -> mapper.readValue("{\"name\":\"Кружка\",\"flags\":" + flags + "}",
                    CreateItemRequest.class)).isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> mapper.readValue("{\"flags\":" + flags + "}", UpdateItemRequest.class))
                    .isInstanceOf(RuntimeException.class);
        }
    }
}
