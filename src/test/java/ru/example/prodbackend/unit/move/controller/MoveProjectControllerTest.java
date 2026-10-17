package ru.example.prodbackend.unit.move.controller;

import ru.example.prodbackend.move.controller.MoveProjectController;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.example.prodbackend.exception.GlobalExceptionHandler;
import ru.example.prodbackend.exception.MoveProjectNotFoundException;
import ru.example.prodbackend.common.dto.PageResponse;
import ru.example.prodbackend.move.dto.response.MoveProjectResponse;
import ru.example.prodbackend.move.service.MoveProjectService;
import ru.example.prodbackend.user.entity.UserEntity;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MoveProjectControllerTest {

    private static final String URL = "/api/move-projects";
    private static final UUID PROJECT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final String CREATE_BODY = """
            {"name":"Переезд", "moveDate":"2026-11-01T09:00:00Z"}
            """;

    private MoveProjectService service;
    private MockMvc mockMvc;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        service = mock(MoveProjectService.class);
        user = UserEntity.builder().id(UUID.randomUUID()).build();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(user, null));
        mockMvc = MockMvcBuilders.standaloneSetup(new MoveProjectController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createReturns201AndProject() throws Exception {
        when(service.createMoveProject(any(), eq(user))).thenReturn(projectResponse());

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(PROJECT_ID.toString()));
    }

    @Test
    void listReturnsPageWithDefaultPagination() throws Exception {
        when(service.getMoveProjects(any(), isNull(), eq(user))).thenReturn(PageResponse.<MoveProjectResponse>builder()
                .content(List.of(projectResponse())).page(0).size(20).totalElements(1).totalPages(1).build());

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(PROJECT_ID.toString()));

        verify(service).getMoveProjects(argThat(request -> request.getPage() == 0 && request.getSize() == 20), isNull(), eq(user));
    }

    @Test
    void getReturns200AndProject() throws Exception {
        when(service.getMoveProject(PROJECT_ID, user)).thenReturn(projectResponse());

        mockMvc.perform(get(URL + "/" + PROJECT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PROJECT_ID.toString()));
    }

    @Test
    void patchReturns200AndProject() throws Exception {
        when(service.updateMoveProject(eq(PROJECT_ID), any(), eq(user))).thenReturn(projectResponse());

        mockMvc.perform(patch(URL + "/" + PROJECT_ID).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PROJECT_ID.toString()));
    }

    @Test
    void deleteReturns204WithoutBody() throws Exception {
        mockMvc.perform(delete(URL + "/" + PROJECT_ID))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(service).deleteMoveProject(PROJECT_ID, user);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PATCH", "DELETE"})
    void invalidProjectIdReturns400WithErrorSchema(String method) throws Exception {
        mockMvc.perform(projectRequest(method, "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("projectId"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.time").isNotEmpty());

        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PATCH", "DELETE"})
    void missingOrForeignProjectReturns404(String method) throws Exception {
        when(service.getMoveProject(PROJECT_ID, user)).thenThrow(new MoveProjectNotFoundException("Project not found"));
        when(service.updateMoveProject(eq(PROJECT_ID), any(), eq(user))).thenThrow(new MoveProjectNotFoundException("Project not found"));
        doThrow(new MoveProjectNotFoundException("Project not found")).when(service).deleteMoveProject(PROJECT_ID, user);

        mockMvc.perform(projectRequest(method, PROJECT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MOVE_PROJECT_NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=101", "page=text", "size=text"})
    void invalidPaginationReturns422(String query) throws Exception {
        String[] parameter = query.split("=");
        mockMvc.perform(get(URL).param(parameter[0], parameter[1]))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value(parameter[0]));

        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":\"   \",\"moveDate\":\"2026-11-01T09:00:00Z\"}"})
    void invalidCreateBodyReturns422(String body) throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(service);
    }

    @Test
    void invalidPatchNameReturns422() throws Exception {
        mockMvc.perform(patch(URL + "/" + PROJECT_ID).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"   \"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PATCH"})
    void invalidDateReturns400(String method) throws Exception {
        MockHttpServletRequestBuilder request = method.equals("POST") ? post(URL) : patch(URL + "/" + PROJECT_ID);
        mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Переезд\",\"moveDate\":\"invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));

        verifyNoInteractions(service);
    }

    private MoveProjectResponse projectResponse() {
        return MoveProjectResponse.builder().id(PROJECT_ID).name("Переезд").build();
    }

    private MockHttpServletRequestBuilder projectRequest(String method, String projectId) {
        String path = URL + "/" + projectId;
        return switch (method) {
            case "GET" -> get(path);
            case "PATCH" -> patch(path).contentType(MediaType.APPLICATION_JSON).content("{}");
            case "DELETE" -> delete(path);
            default -> throw new IllegalArgumentException("Unknown method: " + method);
        };
    }
}
