package ru.example.prodbackend.integration.system;

import org.junit.jupiter.api.Test;
import ru.example.prodbackend.support.BaseIntegrationTest;
import tools.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpenApiDocumentationIntegrationTest extends BaseIntegrationTest {
    private static final Pattern RUSSIAN = Pattern.compile("[А-Яа-яЁё]");
    private static final Set<String> METHODS = Set.of("get", "post", "put", "patch", "delete", "head", "options");

    private JsonNode documentation() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        Files.writeString(Path.of("target/openapi-documentation.json"), json);
        return jsonMapper.readTree(json);
    }

    @Test
    void documentsAllOperationsParametersAndModelPropertiesInRussian() throws Exception {
        JsonNode api = documentation();
        assertEquals("MoverMate API", api.path("info").path("title").asText());
        Set<String> tags = new HashSet<>();
        for (JsonNode tag : api.path("tags")) {
            russian(tag.path("name"), "Имя группы");
            russian(tag.path("description"), tag.path("name").asText());
            assertTrue(tags.add(tag.path("name").asText()), "Повтор группы");
        }
        api.path("paths").properties().forEach(path -> path.getValue().properties().forEach(method -> {
            if (!METHODS.contains(method.getKey())) return;
            String label = method.getKey() + " " + path.getKey();
            JsonNode operation = method.getValue();
            russian(operation.path("summary"), label);
            russian(operation.path("description"), label);
            assertFalse(operation.path("tags").isEmpty(), label);
            operation.path("tags").forEach(tag -> assertTrue(tags.contains(tag.asText()), label + ": " + tag));
            operation.path("parameters").forEach(parameter ->
                    russian(parameter.path("description"), label + ": " + parameter.path("name")));
            checkProperties(operation.path("requestBody"), label);
        }));
        api.path("components").path("schemas").properties().forEach(schema -> checkProperties(schema.getValue(), schema.getKey()));
    }

    private void checkProperties(JsonNode node, String label) {
        if (node.isObject()) {
            node.path("properties").properties().forEach(property -> {
                russian(property.getValue().path("description"), label + "." + property.getKey());
            });
            node.properties().forEach(entry -> checkProperties(entry.getValue(), label + "/" + entry.getKey()));
        } else if (node.isArray()) {
            node.forEach(value -> checkProperties(value, label));
        }
    }

    @Test
    void preservesSecurityErrorsPaginationAndBothPackingMediaTypes() throws Exception {
        JsonNode api = documentation();
        var bearer = api.path("components").path("securitySchemes").path("jwtAuth");
        assertEquals("http", bearer.path("type").asText());
        assertEquals("bearer", bearer.path("scheme").asText());
        api.path("paths").properties().forEach(path -> path.getValue().properties().forEach(method -> {
            if (!METHODS.contains(method.getKey())) return;
            JsonNode operation = method.getValue();
            String label = method.getKey() + " " + path.getKey();
            boolean publicEndpoint = path.getKey().startsWith("/api/auth/") || path.getKey().equals("/api/demo/ping");
            if (publicEndpoint) assertTrue(operation.path("security").isEmpty(), label);
            else assertTrue(operation.path("security").toString().contains("jwtAuth"), label);
            assertTrue(operation.path("responses").properties().stream().anyMatch(entry -> entry.getKey().matches("2\\d\\d")), label + ": нет успешного ответа");
            operation.path("responses").properties().forEach(response -> {
                russian(response.getValue().path("description"), label + ": " + response.getKey());
                JsonNode value = response.getValue();
                if (response.getKey().equals("204")) assertTrue(value.path("content").isMissingNode(), label);
                if (!response.getKey().matches("[45]\\d\\d")) return;
                assertTrue(value.path("description").asText().matches("(?s)[A-Z][A-Z_]+: .+"), label + ": " + value);
                assertEquals("#/components/schemas/ErrorResponse",
                        value.path("content").path("application/json").path("schema").path("$ref").asText(), label);
                value.path("content").path("application/json").path("examples").properties().forEach(example -> {
                    assertEquals(example.getKey(), example.getValue().path("value").path("code").asText(), label);
                    assertTrue(value.path("description").asText().contains(example.getKey() + ":"), label);
                    russian(example.getValue().path("summary"), label);
                });
            });
        }));
        JsonNode packing = api.path("paths").path("/api/ml/room-packing-plan").path("post").path("requestBody").path("content");
        assertTrue(packing.has("application/json"));
        assertTrue(packing.has("multipart/form-data"));
        String multipartRef = packing.path("multipart/form-data").path("schema").path("$ref").asText();
        JsonNode multipart = api.path("components").path("schemas").path(multipartRef.substring(multipartRef.lastIndexOf('/') + 1));
        assertTrue(multipart.path("properties").has("description"));
        assertTrue(multipart.path("properties").has("photo"));
        assertTrue(api.path("paths").path("/api/ml/room-packing-plan").path("post").path("parameters").isMissingNode());
        JsonNode register = api.path("paths").path("/api/auth/register").path("post");
        JsonNode validation = register.path("responses").path("422").path("content").path("application/json")
                .path("examples").path("VALIDATION_ERROR").path("value");
        assertEquals("Request validation failed", validation.path("message").asText());
        assertEquals("email is blank", validation.path("fieldErrors").path(0).path("issue").asText());
        for (String pageType : Set.of("PageResponseTaskTemplateResponse", "PageResponseTaskSetSummaryResponse")) {
            assertTrue(api.path("components").path("schemas").has(pageType), pageType);
        }
        JsonNode page = api.path("components").path("schemas").path("PageResponseMoveProjectResponse");
        assertEquals("#/components/schemas/MoveProjectResponse", page.path("properties").path("content").path("items").path("$ref").asText());
        assertFalse(api.path("paths").path("/api/move-projects/{projectId}/items").path("get").path("responses").has("404"));
        assertFalse(api.path("paths").path("/api/move-projects/{projectId}/boxes/{boxId}/items").path("get").path("responses").has("409"));
    }

    @Test
    void validationExampleMatchesActualServerMessages() throws Exception {
        JsonNode api = documentation();
        JsonNode example = api.path("paths").path("/api/auth/register").path("post").path("responses")
                .path("422").path("content").path("application/json").path("examples").path("VALIDATION_ERROR").path("value");
        String json = mockMvc.perform(post("/api/auth/register").contentType("application/json")
                        .content("{\"email\":\"\",\"firstName\":\"Иван\",\"lastName\":\"Иванов\",\"password\":\"password123\"}"))
                .andExpect(status().isUnprocessableContent()).andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        JsonNode actual = jsonMapper.readTree(json);
        for (String field : Set.of("code", "message", "fieldErrors")) assertEquals(example.path(field), actual.path(field), field);
    }

    @Test
    void documentsAssignmentAndMembershipRevocation() throws Exception {
        var api = documentation();
        for (String type : Set.of("CreateMoveTaskRequest", "UpdateMoveTaskRequest", "ApplyTaskTemplateRequest", "MoveTaskResponse")) {
            var properties = api.path("components").path("schemas").path(type).path("properties");
            assertEquals("uuid", properties.path("assigneeId").path("format").asText(), type);
            assertTrue(properties.has("assigneeName"), type);
            assertFalse(properties.has("assigneeIdPresent"), type);
        }
        var operation = api.path("paths").path("/api/move-projects/{projectId}/participants/{userId}").path("delete");
        assertFalse(operation.isMissingNode());
        assertTrue(operation.path("requestBody").isMissingNode());
        assertFalse(operation.path("responses").path("204").has("content"));
        for (String code : Set.of("204", "400", "401", "404", "409")) assertTrue(operation.path("responses").has(code));
    }

    private void russian(JsonNode text, String label) {
        assertTrue(text.isString() && RUSSIAN.matcher(text.asText()).find(), () -> label + ": требуется описание на русском, получено " + text);
    }
}
