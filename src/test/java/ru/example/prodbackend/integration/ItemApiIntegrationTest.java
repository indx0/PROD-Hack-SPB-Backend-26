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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import ru.example.prodbackend.auth.service.JwtService;
import ru.example.prodbackend.box.entity.BoxEntity;
import ru.example.prodbackend.box.entity.BoxStatus;
import ru.example.prodbackend.config.TestcontainersConfig;
import ru.example.prodbackend.item.entity.ItemEntity;
import ru.example.prodbackend.item.repository.ItemRepository;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import ru.example.prodbackend.room.entity.RoomEntity;
import ru.example.prodbackend.user.entity.UserEntity;
import ru.example.prodbackend.user.entity.UserRole;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@Transactional
class ItemApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private ItemRepository itemRepository;

    private UserEntity owner;
    private UserEntity stranger;
    private MoveProjectEntity project;
    private BoxEntity sourceBox;
    private BoxEntity matchingBox;
    private ItemEntity source;
    private String token;
    private String projectUrl;
    private String collectionUrl;
    private String itemUrl;

    @BeforeEach
    void setUp() {
        owner = user();
        stranger = user();
        project = project(owner);
        RoomEntity kitchen = room(project, "Кухня");
        RoomEntity bedroom = room(project, "Спальня");
        sourceBox = box(project, kitchen, 3, BoxStatus.PACKING);
        matchingBox = box(project, bedroom, 1, BoxStatus.LOADED);
        source = item(sourceBox, "Зарядка", true);
        item(matchingBox, "ЗАРЯДКА USB", false);
        item(box(project, kitchen, 2, BoxStatus.DELIVERED), "Чайник", false);
        item(box(project, kitchen, 4, BoxStatus.UNPACKED), "Зарядка", false);
        item(box(project, bedroom, 5, BoxStatus.PACKING), "Книга", false);
        MoveProjectEntity foreignProject = project(stranger);
        item(box(foreignProject, room(foreignProject, "Кухня"), 1, BoxStatus.PACKING), "Зарядка", true);
        entityManager.flush();
        entityManager.clear();
        token = jwtService.generateToken(owner);
        projectUrl = "/api/move-projects/" + project.getId();
        collectionUrl = projectUrl + "/boxes/" + sourceBox.getId() + "/items";
        itemUrl = collectionUrl + "/" + source.getId();
    }

    @Test
    void createsTrimmedItemWithoutChangingBox() throws Exception {
        String body = perform(post(collectionUrl), "{\"name\":\"  Стакан  \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Стакан"))
                .andExpect(jsonPath("$.lost").value(false))
                .andExpect(jsonPath("$.boxNumber").value(3))
                .andExpect(jsonPath("$.roomName").value("Кухня"))
                .andExpect(jsonPath("$.boxStatus").value("PACKING"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(jsonMapper.readTree(body).get("id").asText());
        entityManager.clear();
        assertThat(entityManager.find(ItemEntity.class, id).getName()).isEqualTo("Стакан");
        BoxEntity unchanged = entityManager.find(BoxEntity.class, sourceBox.getId());
        assertThat(unchanged.getStatus()).isEqualTo(BoxStatus.PACKING);
        assertThat(unchanged.isContentsDeferred()).isTrue();
    }

    @Test
    void createsBatchInInputOrderAndKeepsDuplicateNames() throws Exception {
        perform(post(collectionUrl + "/batch"), "{\"names\":[\" Кружка \",\"Тарелка\",\"Кружка\"]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].name").value("Кружка"))
                .andExpect(jsonPath("$[1].name").value("Тарелка"))
                .andExpect(jsonPath("$[2].name").value("Кружка"))
                .andExpect(jsonPath("$[2].lost").value(false));
        assertThat(countSourceItems()).isEqualTo(4);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":null}", "{\"name\":\"\"}", "{\"name\":\"   \"}"})
    void rejectsMissingOrBlankName(String body) throws Exception {
        perform(post(collectionUrl), body).andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(countSourceItems()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"names\":null}", "{\"names\":[]}",
            "{\"names\":[\"Valid\",\" \"]}", "{\"names\":[\"Valid\",null]}"})
    void rejectsEntireInvalidBatch(String body) throws Exception {
        perform(post(collectionUrl + "/batch"), body).andExpect(status().is(422));
        assertThat(countSourceItems()).isEqualTo(1);
    }

    @Test
    void enforcesNameAndBatchSizeLimits() throws Exception {
        perform(post(collectionUrl), jsonMapper.writeValueAsString(Map.of("name", "x".repeat(256))))
                .andExpect(status().is(422));
        perform(post(collectionUrl + "/batch"), jsonMapper.writeValueAsString(
                Map.of("names", Collections.nCopies(101, "Item"))))
                .andExpect(status().is(422));
        perform(post(collectionUrl + "/batch"), jsonMapper.writeValueAsString(
                Map.of("names", List.of("Good", "x".repeat(256)))))
                .andExpect(status().is(422));
        assertThat(countSourceItems()).isEqualTo(1);
    }

    @Test
    void listsOnlyBoxContentsWithPaginationAndStableOrder() throws Exception {
        perform(post(collectionUrl + "/batch"), "{\"names\":[\"A\",\"B\"]}")
                .andExpect(status().isCreated());
        perform(get(collectionUrl + "?size=1&page=0"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(source.getId().toString()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.pageable").doesNotExist())
                .andExpect(jsonPath("$.sort").doesNotExist());
        perform(get(collectionUrl + "?size=1&page=3"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void updatesNameAndCanSetAndClearLostFlag() throws Exception {
        Instant createdAt = reloadSource().getCreatedAt();
        perform(patch(itemUrl), "{\"name\":\" USB зарядка \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("USB зарядка"))
                .andExpect(jsonPath("$.lost").value(true));
        perform(patch(itemUrl), "{\"lost\":false}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.lost").value(false));
        perform(patch(itemUrl), "{\"lost\":true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.lost").value(true));
        ItemEntity updated = reloadSource();
        assertThat(updated.getName()).isEqualTo("USB зарядка");
        assertThat(updated.isLost()).isTrue();
        assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
        assertThat(updated.getBox().getId()).isEqualTo(sourceBox.getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":\"\"}", "{\"name\":\"  \"}"})
    void rejectsInvalidPatchWithoutChangingItem(String body) throws Exception {
        perform(patch(itemUrl), body).andExpect(status().is(422));
        assertThat(reloadSource().getName()).isEqualTo("Зарядка");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"name\":null}", "{\"lost\":null}", "{\"boxId\":\"anything\"}", "{"})
    void rejectsNullsUnknownFieldsAndMalformedJson(String body) throws Exception {
        perform(patch(itemUrl), body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
        assertThat(reloadSource().getName()).isEqualTo("Зарядка");
        assertThat(reloadSource().isLost()).isTrue();
    }

    @Test
    void deletesOnlyRequestedItem() throws Exception {
        perform(delete(itemUrl), null).andExpect(status().isNoContent()).andExpect(content().string(""));
        entityManager.clear();
        assertThat(entityManager.find(ItemEntity.class, source.getId())).isNull();
        assertThat(entityManager.find(BoxEntity.class, sourceBox.getId())).isNotNull();
        assertThat(itemRepository.findAllByBoxId(matchingBox.getId(), org.springframework.data.domain.PageRequest.of(0, 20))
                .getTotalElements()).isEqualTo(1);
    }

    @Test
    void searchesCaseInsensitivelyAcrossProjectWithBoxContextAndLostFilter() throws Exception {
        perform(get(projectUrl + "/items").param("q", "  зАрЯдКа  "), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].roomName").exists())
                .andExpect(jsonPath("$.content[0].boxStatus").exists());
        perform(get(projectUrl + "/items").param("q", "зарядка").param("lost", "true"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(source.getId().toString()));
        perform(get(projectUrl + "/items").param("q", "зарядка").param("lost", "false"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        perform(get(projectUrl + "/items").param("q", "Несуществующая вещь"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void escapesLikeWildcardsAndProducesRepeatableSearchPages() throws Exception {
        item(entityManager.getReference(BoxEntity.class, sourceBox.getId()), "100%_!", false);
        item(entityManager.getReference(BoxEntity.class, sourceBox.getId()), "100abc", false);
        entityManager.flush();
        for (String query : List.of("%", "_", "!")) {
            perform(get(projectUrl + "/items").param("q", query), null)
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].name").value("100%_!"));
        }
        String first = perform(get(projectUrl + "/items").param("q", "зарядка").param("size", "1"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andReturn().getResponse().getContentAsString();
        perform(get(projectUrl + "/items").param("q", "зарядка").param("size", "1"), null)
                .andExpect(content().json(first));
    }

    @Test
    void ranksSuggestionsByContentsThenRoomAndExcludesUnpackedAndForeignBoxes() throws Exception {
        perform(get(projectUrl + "/items/" + source.getId() + "/suggestions"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].boxNumber").value(3))
                .andExpect(jsonPath("$[0].contentsMatch").value(true))
                .andExpect(jsonPath("$[0].sameRoom").value(true))
                .andExpect(jsonPath("$[1].boxNumber").value(1))
                .andExpect(jsonPath("$[1].contentsMatch").value(true))
                .andExpect(jsonPath("$[1].sameRoom").value(false))
                .andExpect(jsonPath("$[2].boxNumber").value(2))
                .andExpect(jsonPath("$[2].contentsMatch").value(false));
        perform(get(projectUrl + "/items/" + source.getId() + "/suggestions?limit=2"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
        assertThat(reloadSource().isLost()).isTrue();
    }

    @Test
    void wrongBoxAndMissingItemCannotBeUpdatedOrDeleted() throws Exception {
        String wrongBoxUrl = projectUrl + "/boxes/" + matchingBox.getId() + "/items/" + source.getId();
        perform(patch(wrongBoxUrl), "{\"lost\":false}").andExpect(status().isNotFound());
        perform(delete(wrongBoxUrl), null).andExpect(status().isNotFound());
        perform(patch(collectionUrl + "/" + UUID.randomUUID()), "{\"lost\":false}")
                .andExpect(status().isNotFound());
        assertThat(reloadSource().isLost()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"GET, list", "POST, create", "POST, batch", "PATCH, update", "DELETE, delete", "GET, suggestions"})
    void deniesForeignUserForAllResourceOperations(String method, String operation) throws Exception {
        mockMvc.perform(operationRequest(method, operation)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateToken(stranger)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        assertThat(countSourceItems()).isEqualTo(1);
        assertThat(reloadSource().isLost()).isTrue();
    }

    @Test
    void searchNeverDisclosesForeignOrMissingProjects() throws Exception {
        mockMvc.perform(get(projectUrl + "/items").param("q", "зарядка")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateToken(stranger)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)));
        perform(get("/api/move-projects/" + UUID.randomUUID() + "/items").param("q", "зарядка"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)));
    }

    @ParameterizedTest
    @CsvSource({"GET, list", "POST, create", "POST, batch", "PATCH, update", "DELETE, delete",
            "GET, search", "GET, suggestions"})
    void requiresJwtOnEveryEndpoint(String method, String operation) throws Exception {
        mockMvc.perform(operationRequest(method, operation))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void rejectsBlankSearch(String query) throws Exception {
        perform(get(projectUrl + "/items").param("q", query), null).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 101})
    void validatesSuggestionLimit(int limit) throws Exception {
        perform(get(projectUrl + "/items/" + source.getId() + "/suggestions?limit=" + limit), null)
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"list", "search"})
    void returnsCommonPageResponseWithDefaultPagination(String operation) throws Exception {
        String url = operation.equals("list") ? collectionUrl : projectUrl + "/items?q=зарядка";
        perform(get(url), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(operation.equals("list") ? 1 : 3))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.pageable").doesNotExist())
                .andExpect(jsonPath("$.number").doesNotExist());
    }

    @ParameterizedTest
    @CsvSource({
            "list, page=-1", "list, size=0", "list, size=-1", "list, size=101",
            "list, page=invalid", "list, size=invalid",
            "search, page=-1", "search, size=0", "search, size=-1", "search, size=101",
            "search, page=invalid", "search, size=invalid"
    })
    void rejectsInvalidPagination(String operation, String query) throws Exception {
        String url = operation.equals("list") ? collectionUrl + "?" : projectUrl + "/items?q=зарядка&";
        perform(get(url + query), null).andExpect(status().is4xxClientError());
    }

    private MockHttpServletRequestBuilder operationRequest(String method, String operation) {
        String url = switch (operation) {
            case "list", "create" -> collectionUrl;
            case "batch" -> collectionUrl + "/batch";
            case "update", "delete" -> itemUrl;
            case "search" -> projectUrl + "/items?q=зарядка";
            case "suggestions" -> projectUrl + "/items/" + source.getId() + "/suggestions";
            default -> throw new IllegalArgumentException(operation);
        };
        String body = switch (operation) {
            case "create" -> "{\"name\":\"Чайник\"}";
            case "batch" -> "{\"names\":[\"Чайник\"]}";
            case "update" -> "{\"lost\":false}";
            default -> "{}";
        };
        return request(HttpMethod.valueOf(method), url).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private ItemEntity reloadSource() {
        entityManager.clear();
        return entityManager.find(ItemEntity.class, source.getId());
    }

    private long countSourceItems() {
        return itemRepository.findAllByBoxId(sourceBox.getId(), org.springframework.data.domain.PageRequest.of(0, 20))
                .getTotalElements();
    }

    private UserEntity user() {
        UserEntity user = UserEntity.builder().email("item-api-" + UUID.randomUUID())
                .password("test-password-hash").role(UserRole.USER).build();
        entityManager.persist(user);
        return user;
    }

    private MoveProjectEntity project(UserEntity owner) {
        MoveProjectEntity project = MoveProjectEntity.builder().owner(owner).name("Переезд")
                .moveDate(Instant.parse("2026-10-30T00:00:00Z")).build();
        entityManager.persist(project);
        return project;
    }

    private RoomEntity room(MoveProjectEntity project, String name) {
        RoomEntity room = RoomEntity.builder().project(project).name(name).build();
        entityManager.persist(room);
        return room;
    }

    private BoxEntity box(MoveProjectEntity project, RoomEntity room, int number, BoxStatus status) {
        BoxEntity box = BoxEntity.builder().project(project).room(room).number(number)
                .status(status).contentsDeferred(true).build();
        entityManager.persist(box);
        return box;
    }

    private ItemEntity item(BoxEntity box, String name, boolean lost) {
        ItemEntity item = ItemEntity.builder().box(box).name(name).lost(lost).build();
        entityManager.persist(item);
        return item;
    }
}
