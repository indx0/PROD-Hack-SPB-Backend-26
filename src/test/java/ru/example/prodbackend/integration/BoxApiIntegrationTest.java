package ru.example.prodbackend.integration;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import ru.example.prodbackend.auth.service.JwtService;
import ru.example.prodbackend.box.entity.BoxEntity;
import ru.example.prodbackend.box.entity.BoxStatus;
import ru.example.prodbackend.config.TestcontainersConfig;
import ru.example.prodbackend.item.entity.ItemEntity;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import ru.example.prodbackend.room.entity.RoomEntity;
import ru.example.prodbackend.user.entity.UserEntity;
import ru.example.prodbackend.user.entity.UserRole;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@Transactional
class BoxApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JwtService jwtService;

    private MoveProjectEntity project;
    private RoomEntity room;
    private BoxEntity box;
    private String token;
    private String baseUrl;
    private String boxUrl;

    @BeforeEach
    void setUp() {
        UserEntity owner = user();
        project = MoveProjectEntity.builder().owner(owner).name("Переезд")
                .moveDate(Instant.parse("2026-10-30T00:00:00Z")).build();
        entityManager.persist(project);
        room = RoomEntity.builder().project(project).name("Кухня").build();
        entityManager.persist(room);
        box = BoxEntity.builder().project(project).room(room).number(1)
                .contentsDeferred(true).build();
        entityManager.persist(box);
        entityManager.flush();
        entityManager.clear();
        token = jwtService.generateToken(owner);
        baseUrl = "/api/projects/" + project.getId() + "/boxes";
        boxUrl = baseUrl + "/" + box.getId();
    }

    @Test
    void returnsBoxCardWithoutExposingEntities() throws Exception {
        mockMvc.perform(get(boxUrl).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(box.getId().toString()))
                .andExpect(jsonPath("$.projectId").value(project.getId().toString()))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.roomId").value(room.getId().toString()))
                .andExpect(jsonPath("$.roomName").value("Кухня"))
                .andExpect(jsonPath("$.status").value("PACKING"))
                .andExpect(jsonPath("$.contentsDeferred").value(true))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.project").doesNotExist())
                .andExpect(jsonPath("$.room").doesNotExist());
    }

    @Test
    void partialUpdatePersistsFalseAndPreservesOtherFields() throws Exception {
        Instant createdAt = reloadBox().getCreatedAt();

        patchJson(boxUrl, "{\"contentsDeferred\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentsDeferred").value(false))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.status").value("PACKING"));

        BoxEntity updated = reloadBox();
        assertThat(updated.isContentsDeferred()).isFalse();
        assertThat(updated.getRoom().getId()).isEqualTo(room.getId());
        assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(createdAt);
    }

    @Test
    void changesNumberAndAcceptsCurrentRoomWithoutChangingStatus() throws Exception {
        patchJson(boxUrl, "{\"number\":12,\"roomId\":\"" + room.getId() + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(12))
                .andExpect(jsonPath("$.contentsDeferred").value(true))
                .andExpect(jsonPath("$.roomId").value(room.getId().toString()));

        assertThat(reloadBox().getNumber()).isEqualTo(12);
    }

    @Test
    void duplicateNumberRejectsWholeUpdate() throws Exception {
        BoxEntity otherBox = BoxEntity.builder()
                .project(entityManager.getReference(MoveProjectEntity.class, project.getId()))
                .room(entityManager.getReference(RoomEntity.class, room.getId()))
                .number(2).build();
        entityManager.persist(otherBox);
        entityManager.flush();

        patchJson(boxUrl, "{\"number\":2,\"contentsDeferred\":false}")
                .andExpect(status().isConflict());

        BoxEntity unchanged = reloadBox();
        assertThat(unchanged.getNumber()).isEqualTo(1);
        assertThat(unchanged.isContentsDeferred()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"number\":0}", "{\"number\":-1}"})
    void rejectsInvalidUpdateWithValidationError(String body) throws Exception {
        patchJson(boxUrl, body)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty());

        assertThat(reloadBox().getNumber()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"number\":null}", "{\"roomId\":null}", "{\"contentsDeferred\":null}",
            "{\"status\":\"UNPACKED\"}", "{\"unknown\":true}", "{",
            "{\"roomId\":\"not-a-uuid\"}"
    })
    void rejectsMalformedOrUnsupportedUpdateFields(String body) throws Exception {
        patchJson(boxUrl, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));

        BoxEntity unchanged = reloadBox();
        assertThat(unchanged.getNumber()).isEqualTo(1);
        assertThat(unchanged.getStatus()).isEqualTo(BoxStatus.PACKING);
        assertThat(unchanged.isContentsDeferred()).isTrue();
    }

    @Test
    void advancesThroughLifecycleAndRepeatsStatusesWithoutChangingTimestamp() throws Exception {
        for (String next : new String[]{"PACKING", "PACKED", "LOADED", "DELIVERED", "UNPACKED"}) {
            patchJson(boxUrl + "/status", "{\"status\":\"" + next + "\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(next));
            BoxEntity updated = reloadBox();
            Instant updatedAt = updated.getUpdatedAt();
            assertThat(updated.getStatus().name()).isEqualTo(next);

            patchJson(boxUrl + "/status", "{\"status\":\"" + next + "\"}")
                    .andExpect(status().isOk());
            assertThat(reloadBox().getUpdatedAt()).isEqualTo(updatedAt);
        }
    }

    @ParameterizedTest
    @CsvSource({"PACKING, LOADED", "PACKING, DELIVERED", "PACKING, UNPACKED", "PACKED, PACKING"})
    void rejectsInvalidStatusTransitionWithoutPersistingIt(BoxStatus current, BoxStatus next) throws Exception {
        if (current == BoxStatus.PACKED) {
            patchJson(boxUrl + "/status", "{\"status\":\"PACKED\"}")
                    .andExpect(status().isOk());
        }
        Instant updatedAt = reloadBox().getUpdatedAt();

        patchJson(boxUrl + "/status", "{\"status\":\"" + next + "\"}")
                .andExpect(status().isConflict());

        BoxEntity unchanged = reloadBox();
        assertThat(unchanged.getStatus()).isEqualTo(current);
        assertThat(unchanged.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"status\":null}"})
    void requiresStatus(String body) throws Exception {
        patchJson(boxUrl + "/status", body)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(reloadBox().getStatus()).isEqualTo(BoxStatus.PACKING);
    }

    @Test
    void rejectsUnknownStatus() throws Exception {
        patchJson(boxUrl + "/status", "{\"status\":\"UNKNOWN\"}")
                .andExpect(status().isBadRequest());
        assertThat(reloadBox().getStatus()).isEqualTo(BoxStatus.PACKING);
    }

    @Test
    void deletesBoxWithItemsAndReturnsNoContent() throws Exception {
        ItemEntity item = ItemEntity.builder().name("Чайник")
                .box(entityManager.getReference(BoxEntity.class, box.getId())).build();
        entityManager.persist(item);
        entityManager.flush();

        mockMvc.perform(delete(boxUrl).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        entityManager.clear();

        assertThat(entityManager.find(BoxEntity.class, box.getId())).isNull();
        assertThat(entityManager.find(ItemEntity.class, item.getId())).isNull();
    }

    @ParameterizedTest
    @CsvSource({"GET,", "PATCH,", "PATCH, /status", "DELETE,"})
    void hidesForeignBoxForAllImplementedOperations(String method, String suffix) throws Exception {
        UserEntity stranger = user();
        entityManager.flush();

        mockMvc.perform(request(HttpMethod.valueOf(method), boxUrl + (suffix == null ? "" : suffix))
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtService.generateToken(stranger)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(suffix == null ? "{\"number\":12}" : "{\"status\":\"PACKED\"}"))
                .andExpect(status().isNotFound());

        BoxEntity unchanged = reloadBox();
        assertThat(unchanged.getNumber()).isEqualTo(1);
        assertThat(unchanged.getStatus()).isEqualTo(BoxStatus.PACKING);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-box", "wrong-project"})
    void returns404ForMissingBoxOrMismatchedProject(String scenario) throws Exception {
        String url = scenario.equals("missing-box")
                ? baseUrl + "/" + UUID.randomUUID()
                : "/api/projects/" + UUID.randomUUID() + "/boxes/" + box.getId();

        mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @CsvSource({"POST,", "GET,", "GET, /summary", "GET, /box", "PATCH, /box",
            "PATCH, /box/status", "DELETE, /box"})
    void requiresJwtOnEveryEndpoint(String method, String suffix) throws Exception {
        String url = baseUrl + (suffix == null ? "" : suffix.replace("/box", "/" + box.getId()));

        mockMvc.perform(request(HttpMethod.valueOf(method), url))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"contentsDeferred\":false}",
            "{\"number\":1,\"contentsDeferred\":false}",
            "{\"roomId\":\"00000000-0000-0000-0000-000000000001\",\"contentsDeferred\":false}",
            "{\"number\":0,\"roomId\":\"00000000-0000-0000-0000-000000000001\",\"contentsDeferred\":false}"
    })
    void validatesCreateRequestBeforeCallingUnfinishedService(String body) throws Exception {
        mockMvc.perform(post(baseUrl).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"number=0", "number=-1", "status=UNKNOWN", "roomId=invalid"})
    void rejectsInvalidListFiltersWithSharedValidationError(String query) throws Exception {
        mockMvc.perform(get(baseUrl + "?" + query).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
    }

    private ResultActions patchJson(String url, String body) throws Exception {
        return mockMvc.perform(patch(url).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private BoxEntity reloadBox() {
        entityManager.clear();
        return entityManager.find(BoxEntity.class, box.getId());
    }

    private UserEntity user() {
        UserEntity user = UserEntity.builder().email("box-api-" + UUID.randomUUID())
                .password("test-password-hash").role(UserRole.USER).build();
        entityManager.persist(user);
        return user;
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
