package ru.example.prodbackend.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ru.example.prodbackend.task.repository.MoveTaskRepository;
import ru.example.prodbackend.task.repository.TaskTemplateRepository;
import tools.jackson.databind.JsonNode;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PersonalTaskTemplateIntegrationTest extends AuthIntegrationTestSupport {
    @Autowired private MoveTaskRepository tasks;
    @Autowired private TaskTemplateRepository templates;
    private String token;
    private static final String URL = "/api/task-templates";
    private static final String ORIGINAL = "{\"title\":\"  Подготовить переноску  \",\"description\":\" Проверить крепления \",\"stage\":\"BEFORE_MOVE\",\"dueDateOffsetDays\":-2}";
    private static final String REPLACEMENT = "{\"title\":\"Поставить миски\",\"stage\":\"AFTER_MOVE\",\"dueDateOffsetDays\":3}";

    @BeforeEach
    void setUpUser() throws Exception { token = registerUser().getAccessToken(); }

    @Test
    void ownCatalogSurvivesNewLoginAndDoesNotCreateProjectTasks() throws Exception {
        var template = save();
        String id = template.get("id").asText();
        assertThat(tasks.count()).isZero();
        assertThat(moveProjectRepository.count()).isZero();
        assertThat(template.get("title").asText()).isEqualTo("Подготовить переноску");
        assertThat(template.get("personal").asBoolean()).isTrue();
        token = authenticateUser(EMAIL).getAccessToken();
        call(get(URL + "/" + id), null, token).andExpect(status().isOk()).andExpect(jsonPath("$.dueDateOffsetDays").value(-2));
        call(get(URL).param("stage", "BEFORE_MOVE").param("size", "1"), null, token)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(6)).andExpect(jsonPath("$.totalPages").value(6));
        call(put(URL + "/" + id), REPLACEMENT, token).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Поставить миски")).andExpect(jsonPath("$.description").isEmpty())
                .andExpect(jsonPath("$.stage").value("AFTER_MOVE")).andExpect(jsonPath("$.dueDateOffsetDays").value(3));
        call(delete(URL + "/" + id), null, token).andExpect(status().isNoContent());
        call(get(URL + "/" + id), null, token).andExpect(status().isNotFound());
        call(get(URL), null, token).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(11));
    }

    @Test
    void applicationCreatesIndependentRelativeCopyAndSoftDeletionPreservesIt() throws Exception {
        String templateId = save().get("id").asText();
        String projectId = createProject(token);
        String apply = "/api/move-projects/" + projectId + "/tasks/from-template";
        var task = body(call(post(apply), "{\"templateId\":\"" + templateId + "\"}", token).andExpect(status().isCreated())
                .andExpect(jsonPath("$.dueType").value("RELATIVE")).andExpect(jsonPath("$.offsetDays").value(-2)));
        assertThat(task.get("dueDate").asText()).isEqualTo("2026-11-08");
        call(post(apply), "{\"templateId\":\"" + templateId + "\"}", token).andExpect(status().isConflict());
        String taskUrl = "/api/move-projects/" + projectId + "/tasks/" + task.get("id").asText();
        call(patch(taskUrl), "{\"assigneeName\":\"Анна\"}", token).andExpect(status().isOk());
        call(patch(taskUrl + "/completion"), "{\"completed\":true}", token).andExpect(status().isOk());
        var snapshot = body(call(get(taskUrl), null, token).andExpect(status().isOk()));
        call(put(URL + "/" + templateId), REPLACEMENT, token).andExpect(status().isOk());
        String nextProject = createProject(token);
        call(post("/api/move-projects/" + nextProject + "/tasks/from-template"), "{\"templateId\":\"" + templateId + "\"}", token)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("Поставить миски"))
                .andExpect(jsonPath("$.dueDate").value("2026-11-13"));
        call(delete(URL + "/" + templateId), null, token).andExpect(status().isNoContent());
        assertThat(body(call(get(taskUrl), null, token).andExpect(status().isOk()))).isEqualTo(snapshot);
        String thirdProject = createProject(token);
        call(post("/api/move-projects/" + thirdProject + "/tasks/from-template"), "{\"templateId\":\"" + templateId + "\"}", token)
                .andExpect(status().isNotFound());
        assertThat(tasks.count()).isEqualTo(2);
    }

    @Test
    void participantsCanUseOwnTemplatesButCannotReadOrApplyAnotherUsersCatalog() throws Exception {
        String templateId = save().get("id").asText();
        String ownerToken = token;
        String participant = registerUser("participant@example.com").getAccessToken();
        String projectId = createProject(ownerToken);
        call(post("/api/move-projects/" + projectId + "/participants"), "{\"email\":\"participant@example.com\"}", ownerToken)
                .andExpect(status().isOk());
        call(get(URL + "/" + templateId), null, participant).andExpect(status().isNotFound());
        call(put(URL + "/" + templateId), REPLACEMENT, participant).andExpect(status().isNotFound());
        call(delete(URL + "/" + templateId), null, participant).andExpect(status().isNotFound());
        call(get(URL), null, participant).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(11));
        call(get("/api/move-projects/" + projectId + "/tasks/templates"), null, participant)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(11));
        String apply = "/api/move-projects/" + projectId + "/tasks/from-template";
        call(post(apply), "{\"templateId\":\"" + templateId + "\"}", participant).andExpect(status().isNotFound());
        token = participant;
        String ownId = save().get("id").asText();
        call(post(apply), "{\"templateId\":\"" + ownId + "\"}", participant).andExpect(status().isCreated());
        call(get("/api/move-projects/" + projectId + "/tasks"), null, ownerToken)
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].templateId").value(ownId));
        call(put("/api/move-projects/" + projectId + "/archive"), null, ownerToken).andExpect(status().isOk());
        call(put(URL + "/" + ownId), REPLACEMENT, participant).andExpect(status().isOk());
        call(post(apply), "{\"templateId\":\"" + templateId + "\"}", ownerToken).andExpect(status().isConflict());
    }

    @Test
    void commonTemplatesAreReadOnlyAndPersonalDuplicatesAreAllowed() throws Exception {
        var common = templates.findAll().get(0);
        call(put(URL + "/" + common.getId()), REPLACEMENT, token).andExpect(status().isNotFound());
        call(delete(URL + "/" + common.getId()), null, token).andExpect(status().isNotFound());
        var data = jsonMapper.writeValueAsString(Map.of("title", common.getTitle(), "stage", common.getStage(), "dueDateOffsetDays", 0));
        call(post(URL), data, token).andExpect(status().isCreated());
        call(post(URL), data, token).andExpect(status().isCreated());
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"title\":\" \",\"stage\":\"BEFORE_MOVE\",\"dueDateOffsetDays\":0}",
            "{\"title\":\"Task\",\"stage\":\"BEFORE_MOVE\"}", "{\"title\":\"Task\",\"stage\":null,\"dueDateOffsetDays\":0}",
            "{\"title\":\"Task\",\"stage\":\"BEFORE_MOVE\",\"dueDateOffsetDays\":null}"})
    void rejectsIncompleteTemplates(String data) throws Exception {
        call(post(URL), data, token).andExpect(status().isUnprocessableEntity());
        assertThat(templates.count()).isEqualTo(11);
    }

    private JsonNode save() throws Exception { return body(call(post(URL), ORIGINAL, token).andExpect(status().isCreated())); }
    private String createProject(String auth) throws Exception {
        return body(call(post("/api/move-projects"), "{\"name\":\"Переезд\",\"moveDate\":\"2026-11-10T09:00:00Z\"}", auth)
                .andExpect(status().isCreated())).get("id").asText();
    }
    private ResultActions call(MockHttpServletRequestBuilder request, String data, String auth) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + auth);
        if (data != null) request.contentType(MediaType.APPLICATION_JSON).content(data);
        return mockMvc.perform(request);
    }
    private JsonNode body(ResultActions result) throws Exception {
        return jsonMapper.readTree(result.andReturn().getResponse().getContentAsByteArray());
    }
}
