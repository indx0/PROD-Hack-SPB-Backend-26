package ru.example.prodbackend.integration.ml;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import ru.example.prodbackend.auth.service.JwtService;
import ru.example.prodbackend.box.entity.BoxEntity;
import ru.example.prodbackend.box.entity.BoxStatus;
import ru.example.prodbackend.item.entity.ItemEntity;
import ru.example.prodbackend.ml.dto.ItemDescriptionResponse;
import ru.example.prodbackend.ml.dto.ItemsDescriptionResponse;
import ru.example.prodbackend.ml.dto.ObservedItem;
import ru.example.prodbackend.ml.exception.MlException;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import ru.example.prodbackend.room.entity.RoomEntity;
import ru.example.prodbackend.support.BaseMlIntegrationTest;
import ru.example.prodbackend.user.entity.UserEntity;
import ru.example.prodbackend.user.entity.UserRole;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static ru.example.prodbackend.support.fixtures.RoomPackingPlanFixtures.response;

@Transactional
class MlRateLimitIntegrationTest extends BaseMlIntegrationTest {
    @Autowired private EntityManager entities;
    @Autowired private JwtService jwt;
    private UserEntity user;
    private String token;
    private String unpackingUrl;

    @BeforeEach
    void setUp() {
        user = createUser();
        token = jwt.generateToken(user);
        var project = MoveProjectEntity.builder().owner(user).name("Переезд").moveDate(Instant.now()).build();
        entities.persist(project);
        var room = RoomEntity.builder().project(project).name("Кухня").build();
        entities.persist(room);
        var box = BoxEntity.builder().project(project).room(room).number(1).status(BoxStatus.DELIVERED).build();
        entities.persist(box);
        var item = ItemEntity.builder().box(box).name("Кружка").build();
        entities.persist(item);
        entities.flush();
        unpackingUrl = "/api/move-projects/" + project.getId() + "/ml/boxes/" + box.getId() + "/unpacking-verification";
        var suggestion = new ItemDescriptionResponse("Кружка", List.of());
        when(llmClient.describe(any(), any())).thenReturn(suggestion);
        when(llmClient.describeItems(any(), any())).thenReturn(new ItemsDescriptionResponse(List.of(suggestion)));
        when(llmClient.planRoomPacking(anyString(), any(), any())).thenReturn(response());
        when(llmClient.verifyUnpacking(any(), any(), anyList()))
                .thenReturn(List.of(new ObservedItem(item.getId(), "Кружка")));
    }

    @Test
    void allScenariosShareQuotaAndNewTokenDoesNotResetIt() throws Exception {
        for (int i = 0; i < 15; i++) {
            mockMvc.perform(auth(request(i % 5), token)).andExpect(status().isOk());
        }
        assertThat(mockingDetails(llmClient).getInvocations()).hasSize(15);
        clearInvocations(llmClient);
        String newToken = jwt.generateToken(user);
        assertThat(newToken).isNotEqualTo(token);
        for (int scenario = 0; scenario < 5; scenario++) {
            var result = mockMvc.perform(auth(request(scenario), newToken))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("ML_RATE_LIMIT_EXCEEDED"))
                    .andExpect(jsonPath("$.message").value("Превышен лимит запросов к ML API"))
                    .andExpect(jsonPath("$.traceId").isNotEmpty())
                    .andExpect(jsonPath("$.time").isNotEmpty())
                    .andReturn();
            assertThat(Long.parseLong(result.getResponse().getHeader(HttpHeaders.RETRY_AFTER))).isBetween(1L, 60L);
        }
        verifyNoInteractions(llmClient);
        String otherToken = jwt.generateToken(createUser());
        mockMvc.perform(auth(request(0), otherToken)).andExpect(status().isOk());
        verify(llmClient).describe(any(), any());
    }

    @Test
    void unauthorizedInvalidAndInaccessibleRequestsDoNotConsumeQuota() throws Exception {
        for (int i = 0; i < 16; i++) {
            mockMvc.perform(request(0)).andExpect(status().isUnauthorized());
            mockMvc.perform(auth(multipart("/api/ml/item-description")
                            .file(new MockMultipartFile("photo", new byte[0])), token))
                    .andExpect(status().is(422));
            mockMvc.perform(auth(post("/api/ml/room-packing-plan")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"), token))
                    .andExpect(status().is(422));
            mockMvc.perform(auth(multipart("/api/move-projects/" + UUID.randomUUID()
                            + "/ml/boxes/" + UUID.randomUUID() + "/unpacking-verification").file(photo()), token))
                    .andExpect(status().isNotFound());
        }
        verifyNoInteractions(llmClient);
        for (int i = 0; i < 15; i++) mockMvc.perform(auth(request(0), token)).andExpect(status().isOk());
        mockMvc.perform(auth(request(0), token)).andExpect(status().isTooManyRequests());
        verify(llmClient, times(15)).describe(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"LLM_TIMEOUT", "LLM_UNAVAILABLE", "LLM_INVALID_RESPONSE", "IMAGE_NOT_RECOGNIZED"})
    void failedLlmCallsStillConsumeQuota(String code) throws Exception {
        HttpStatus status = switch (code) {
            case "LLM_TIMEOUT" -> HttpStatus.GATEWAY_TIMEOUT;
            case "LLM_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "LLM_INVALID_RESPONSE" -> HttpStatus.BAD_GATEWAY;
            default -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
        when(llmClient.describe(any(), any())).thenThrow(new MlException(status, code, "Ошибка ИИ"));
        for (int i = 0; i < 15; i++) mockMvc.perform(auth(request(0), token)).andExpect(status().is(status.value()));
        mockMvc.perform(auth(request(0), token)).andExpect(status().isTooManyRequests());
        verify(llmClient, times(15)).describe(any(), any());
    }

    private AbstractMockHttpServletRequestBuilder<?> request(int scenario) {
        return switch (scenario) {
            case 0 -> multipart("/api/ml/item-description").file(photo());
            case 1 -> multipart("/api/ml/items-description").file(photo());
            case 2 -> post("/api/ml/room-packing-plan").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"description\":\"Кухня\"}");
            case 3 -> multipart("/api/ml/room-packing-plan").file(photo());
            default -> multipart(unpackingUrl).file(photo());
        };
    }

    private AbstractMockHttpServletRequestBuilder<?> auth(AbstractMockHttpServletRequestBuilder<?> request, String accessToken) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
    }

    private MockMultipartFile photo() {
        return new MockMultipartFile("photo", new byte[]{(byte)255, (byte)216, (byte)255});
    }

    private UserEntity createUser() {
        var created = UserEntity.builder().email("ml-quota-" + UUID.randomUUID()).password("hash")
                .role(UserRole.USER).build();
        entities.persist(created);
        entities.flush();
        return created;
    }
}
