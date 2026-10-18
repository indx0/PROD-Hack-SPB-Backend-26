package ru.example.prodbackend.integration.ml;

import ru.example.prodbackend.support.BaseMlIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import ru.example.prodbackend.auth.service.JwtService;
import ru.example.prodbackend.ml.exception.MlException;
import ru.example.prodbackend.user.entity.UserEntity;
import ru.example.prodbackend.user.entity.UserRole;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static ru.example.prodbackend.support.fixtures.RoomPackingPlanFixtures.*;

@Transactional
class RoomPackingPlanApiIntegrationTest extends BaseMlIntegrationTest {
    private static final String URL = "/api/ml/room-packing-plan";
    private static final byte[] JPEG = {(byte)255, (byte)216, (byte)255};
    @Autowired private MockMvc mvc;
    @Autowired private EntityManager entities;
    @Autowired private JwtService jwt;
    @Autowired private JsonMapper mapper;
    private String token;

    @BeforeEach
    void setUp() {
        var user = UserEntity.builder().email("room-plan-" + UUID.randomUUID()).password("hash").role(UserRole.USER).build();
        entities.persist(user);
        entities.flush();
        token = jwt.generateToken(user);
    }

    @ParameterizedTest
    @ValueSource(strings = {"json", "text_form", "text_empty_photo", "text_empty_photo_part", "photo", "combined"})
    void userWithoutProjectsReceivesPlanWithoutWritingAnyData(String mode) throws Exception {
        when(llmClient.planRoomPacking(anyString(), any(), any())).thenReturn(response());
        var before = counts();
        AbstractMockHttpServletRequestBuilder<?> request = switch (mode) {
            case "json" -> post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"description\":\" Кухня \"}");
            case "text_form" -> multipart(URL).param("description", " Кухня ");
            case "text_empty_photo" -> multipart(URL).param("photo", "").param("description", " Кухня ");
            case "text_empty_photo_part" -> multipart(URL).part(new MockPart("photo", new byte[0]))
                    .part(new MockPart("description", " Кухня ".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            case "photo" -> multipart(URL).file(photo());
            default -> multipart(URL).file(photo()).part(new MockPart("description", " Кухня ".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        };
        mvc.perform(auth(request)).andExpect(status().isOk())
                .andExpect(jsonPath("$.boxCount").value(3))
                .andExpect(jsonPath("$.boxGroups", hasSize(2)))
                .andExpect(jsonPath("$.boxGroups[0].count").value(2))
                .andExpect(jsonPath("$.boxGroups[0].items[0]").value("Кружки"))
                .andExpect(jsonPath("$.packingOrder[0]").value("Упаковать книги"))
                .andExpect(jsonPath("$.declutteringSuggestions[0].action").value("donate"))
                .andExpect(jsonPath("$.assumptions", hasSize(1)));
        assertThat(counts()).isEqualTo(before);
        if (mode.equals("json") || mode.startsWith("text_")) verify(llmClient).planRoomPacking("Кухня", null, null);
        else verify(llmClient).planRoomPacking(mode.equals("photo") ? "" : "Кухня", JPEG, "image/jpeg");
        verifyNoMoreInteractions(llmClient);
    }

    @Test
    void bothFormatsRequireJwtBeforeLlmCall() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"Кухня\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart(URL).file(photo())).andExpect(status().isUnauthorized());
        verifyNoInteractions(llmClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"description\":null}", "{\"description\":\"\"}", "{\"description\":\"   \"}"})
    void missingDescriptionAndPhotoAreRejected(String body) throws Exception {
        mvc.perform(auth(post(URL).contentType(MediaType.APPLICATION_JSON).content(body)))
                .andExpect(status().is(422)).andExpect(jsonPath("$.code").value("ROOM_INPUT_REQUIRED"));
        verifyNoInteractions(llmClient);
    }

    @Test
    void descriptionBoundariesApplyToBothFormatsAfterTrimming() throws Exception {
        when(llmClient.planRoomPacking(anyString(), any(), any())).thenReturn(response());
        String accepted = " " + "x".repeat(4000) + " ";
        mvc.perform(auth(post(URL).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("description", accepted))))).andExpect(status().isOk());
        verify(llmClient).planRoomPacking("x".repeat(4000), null, null);
        clearInvocations(llmClient);
        mvc.perform(auth(post(URL).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("description", "x".repeat(4001))))))
                .andExpect(status().is(422)).andExpect(jsonPath("$.code").value("DESCRIPTION_TOO_LONG"));
        mvc.perform(auth(multipart(URL).file(photo()).param("description", "x".repeat(4001))))
                .andExpect(status().is(422)).andExpect(jsonPath("$.code").value("DESCRIPTION_TOO_LONG"));
        verifyNoInteractions(llmClient);
    }

    @Test
    void validatesMultipartAndPhotoBeforeLlmCall() throws Exception {
        mvc.perform(auth(multipart(URL))).andExpect(status().is(422)).andExpect(jsonPath("$.code").value("ROOM_INPUT_REQUIRED"));
        mvc.perform(auth(multipart(URL).part(new MockPart("photo", new byte[0]))))
                .andExpect(status().is(422)).andExpect(jsonPath("$.code").value("ROOM_INPUT_REQUIRED"));
        mvc.perform(auth(multipart(URL).param("photo", "not-a-file").param("description", "Кухня")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_MULTIPART"));
        mvc.perform(auth(multipart(URL).param("photo", "", "").param("description", "Кухня")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_MULTIPART"));
        mvc.perform(auth(multipart(URL).file(photo()).param("photo", "")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_MULTIPART"));
        mvc.perform(auth(multipart(URL).file(photo()).file(photo()))).andExpect(status().isBadRequest());
        mvc.perform(auth(multipart(URL).file(photo()).param("description", "Кухня", "Спальня"))).andExpect(status().isBadRequest());
        mvc.perform(auth(multipart(URL).file(photo()).param("extra", "value"))).andExpect(status().isBadRequest());
        mvc.perform(auth(multipart(URL).file(photo()).part(new MockPart("extra", new byte[]{1})))).andExpect(status().isBadRequest());
        mvc.perform(auth(multipart(URL).file(new MockMultipartFile("other", JPEG)))).andExpect(status().isBadRequest());
        mvc.perform(auth(multipart(URL).file(new MockMultipartFile("description", JPEG)))).andExpect(status().isBadRequest());
        mvc.perform(auth(multipart(URL).file(new MockMultipartFile("photo", new byte[0])).param("description", "Кухня")))
                .andExpect(status().is(422)).andExpect(jsonPath("$.code").value("EMPTY_IMAGE"));
        mvc.perform(auth(multipart(URL).file(new MockMultipartFile("photo", new byte[]{1}))))
                .andExpect(status().is(415));
        mvc.perform(auth(multipart(URL).file(new MockMultipartFile("photo", new byte[10 * 1024 * 1024 + 1]))))
                .andExpect(status().is(413));
        verifyNoInteractions(llmClient);
    }

    @Test
    void malformedJsonAndUnsupportedContentTypeAreRejected() throws Exception {
        mvc.perform(auth(post(URL).contentType(MediaType.APPLICATION_JSON).content("{"))).andExpect(status().isBadRequest());
        mvc.perform(auth(post(URL).contentType(MediaType.TEXT_PLAIN).content("Кухня"))).andExpect(status().is(415));
        verifyNoInteractions(llmClient);
    }

    @ParameterizedTest
    @CsvSource({"422,ROOM_PLAN_NOT_GENERATED", "502,LLM_INVALID_RESPONSE", "503,LLM_UNAVAILABLE", "504,LLM_TIMEOUT"})
    void preservesUpstreamErrorsAndNeverWritesData(int httpStatus, String code) throws Exception {
        var error = new MlException(org.springframework.http.HttpStatus.valueOf(httpStatus), code, "Не удалось составить план");
        when(llmClient.planRoomPacking(anyString(), any(), any())).thenThrow(error);
        var before = counts();
        mvc.perform(auth(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"Кухня\"}")))
                .andExpect(status().is(httpStatus)).andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.traceId").value(error.getTraceId().toString()));
        assertThat(counts()).isEqualTo(before);
    }

    @Test
    void openApiIncludesBothRequestFormatsAndJwt() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/ml/room-packing-plan'].post.requestBody.content['application/json']").exists())
                .andExpect(jsonPath("$.paths['/api/ml/room-packing-plan'].post.requestBody.content['multipart/form-data']").exists())
                .andExpect(jsonPath("$.paths['/api/ml/room-packing-plan'].post.security[0].jwtAuth").exists());
    }

    private MockMultipartFile photo() { return new MockMultipartFile("photo", "room.jpg", "image/jpeg", JPEG); }
    private AbstractMockHttpServletRequestBuilder<?> auth(AbstractMockHttpServletRequestBuilder<?> request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
    private Map<String, Long> counts() {
        var counts = new LinkedHashMap<String, Long>();
        for (String type : List.of("MoveProjectEntity", "RoomEntity", "BoxEntity", "ItemEntity", "ProjectEventEntity")) {
            counts.put(type, entities.createQuery("select count(e) from " + type + " e", Long.class).getSingleResult());
        }
        return counts;
    }
}
