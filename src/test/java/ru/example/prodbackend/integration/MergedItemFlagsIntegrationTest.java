package ru.example.prodbackend.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MergedItemFlagsIntegrationTest extends AuthIntegrationTestSupport {
    @Autowired private JdbcTemplate jdbc;
    private String token;
    private String projectUrl;
    private String itemsUrl;
    private UUID projectId;

    @BeforeEach
    void setUp() throws Exception {
        token = registerUser().getAccessToken();
        projectId = id(call(post("/api/move-projects"), Map.of("name", "Метки", "moveDate", "2026-11-01T09:00:00Z")).andExpect(status().isCreated()));
        projectUrl = "/api/move-projects/" + projectId;
        UUID room = id(call(post(projectUrl + "/rooms"), Map.of("name", "Кухня")).andExpect(status().isCreated()));
        UUID box = id(call(post("/api/projects/" + projectId + "/boxes"), Map.of("number", 1, "roomId", room)).andExpect(status().isCreated()));
        itemsUrl = projectUrl + "/boxes/" + box + "/items";
    }

    @Test
    void eitherCreateFieldSetsOneSharedFlagAndConflictingInputIsRejected() throws Exception {
        call(post(itemsUrl), Map.of("name", "Зарядка", "firstDay", true)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstDay").value(true)).andExpect(jsonPath("$.flags[0]").value("first_day"));
        call(post(itemsUrl), Map.of("name", "Стаканы", "quantity", 6, "flags", List.of("fragile", "first_day"))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(6)).andExpect(jsonPath("$.firstDay").value(true));
        for (Object body : List.of(Map.of("name", "Ошибка", "firstDay", false, "flags", List.of("first_day")),
                Map.of("name", "Ошибка", "firstDay", true, "flags", List.of()))) {
            assertErrorResponse(call(post(itemsUrl), body), 422, "VALIDATION_ERROR");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM items", Long.class)).isEqualTo(2);
    }

    @Test
    void flagsDetachChecklistAndLegacyFieldPreservesOtherFlags() throws Exception {
        UUID item = id(call(post(itemsUrl), Map.of("name", "Чайник", "flags", List.of("fragile", "first_day", "carry_personally")))
                .andExpect(status().isCreated()));
        UUID point = id(call(post(projectUrl + "/first-day-checklist"), Map.of("name", "Мой чайник", "itemId", item)).andExpect(status().isCreated()));
        call(patch(projectUrl + "/first-day-checklist/" + point), Map.of("completed", true)).andExpect(status().isOk());
        call(patch(itemsUrl + "/" + item), Map.of("flags", List.of("fragile", "carry_personally"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.firstDay").value(false));
        assertThat(jdbc.queryForObject("SELECT item_id FROM first_day_checklist_items WHERE id = ?", UUID.class, point)).isNull();
        assertThat(jdbc.queryForObject("SELECT name FROM first_day_checklist_items WHERE id = ?", String.class, point)).isEqualTo("Мой чайник");
        assertThat(jdbc.queryForObject("SELECT completed_at IS NOT NULL FROM first_day_checklist_items WHERE id = ?", Boolean.class, point)).isTrue();
        call(patch(itemsUrl + "/" + item), Map.of("firstDay", true)).andExpect(status().isOk()).andExpect(jsonPath("$.flags.length()").value(3));
        call(patch(itemsUrl + "/" + item), Map.of("firstDay", false)).andExpect(status().isOk()).andExpect(jsonPath("$.flags.length()").value(2));
        call(get(itemsUrl + "/" + item), null).andExpect(status().isOk()).andExpect(jsonPath("$.flags[0]").value("fragile"))
                .andExpect(jsonPath("$.flags[1]").value("carry_personally"));
    }

    @Test
    void patchConflictRollsBackAllFieldsAndBatchUsesSameContract() throws Exception {
        UUID item = id(call(post(itemsUrl), Map.of("name", "Чайник", "flags", List.of("fragile", "first_day"))).andExpect(status().isCreated()));
        assertErrorResponse(call(patch(itemsUrl + "/" + item), Map.of("name", "Нельзя", "firstDay", false, "flags", List.of("first_day"))), 422, "VALIDATION_ERROR");
        call(get(itemsUrl + "/" + item), null).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Чайник")).andExpect(jsonPath("$.flags.length()").value(2));
        call(post(itemsUrl + "/batch"), Map.of("items", List.of(Map.of("name", "Посуда", "quantity", 6, "flags", List.of("fragile")),
                Map.of("name", "Зарядки", "firstDay", true)))).andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].quantity").value(6)).andExpect(jsonPath("$[0].flags[0]").value("fragile"))
                .andExpect(jsonPath("$[1].flags[0]").value("first_day"));
        long count = jdbc.queryForObject("SELECT count(*) FROM items", Long.class);
        call(post(itemsUrl + "/batch"), Map.of("items", List.of(Map.of("name", "Хорошая"),
                Map.of("name", "Противоречие", "firstDay", true, "flags", List.of())))).andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM items", Long.class)).isEqualTo(count);
    }

    private UUID id(ResultActions result) throws Exception { return UUID.fromString(json(result).get("id").asText()); }
    private JsonNode json(ResultActions result) throws Exception { return jsonMapper.readTree(result.andReturn().getResponse().getContentAsByteArray()); }
    private ResultActions call(MockHttpServletRequestBuilder request, Object body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(body));
        return mockMvc.perform(request);
    }
}
