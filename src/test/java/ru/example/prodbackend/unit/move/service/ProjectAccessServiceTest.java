package ru.example.prodbackend.unit.move.service;

import ru.example.prodbackend.move.service.ProjectAccessService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.example.prodbackend.exception.MoveProjectNotFoundException;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import ru.example.prodbackend.move.repository.MoveProjectRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectAccessServiceTest {

    @Mock
    private MoveProjectRepository repository;

    @InjectMocks
    private ProjectAccessService service;

    @Test
    void returnsProjectFoundByIdAndCurrentUser() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var project = MoveProjectEntity.builder().id(projectId).build();
        when(repository.findAccessibleById(projectId, userId)).thenReturn(Optional.of(project));

        assertSame(project, service.getAccessibleProject(projectId, userId));
        verify(repository).findAccessibleById(projectId, userId);
        verifyNoMoreInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "foreign"})
    void missingAndForeignProjectsProduceSameError(String scenario) {
        UUID ownerId = UUID.randomUUID();
        UUID userId = scenario.equals("foreign") ? UUID.randomUUID() : ownerId;
        UUID projectId = UUID.randomUUID();
        when(repository.findAccessibleById(projectId, userId)).thenReturn(Optional.empty());

        var exception = assertThrows(MoveProjectNotFoundException.class, () -> service.getAccessibleProject(projectId, userId));

        assertEquals("Проект переезда не найден", exception.getMessage());
        verify(repository).findAccessibleById(projectId, userId);
        verifyNoMoreInteractions(repository);
    }
}
