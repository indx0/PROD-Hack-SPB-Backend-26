package ru.example.prodbackend.move.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import ru.example.prodbackend.exception.MoveProjectNotFoundException;
import ru.example.prodbackend.exception.ResourceConflictException;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import ru.example.prodbackend.move.repository.MoveProjectRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectAccessService {

    private final MoveProjectRepository moveProjectRepository;

    public MoveProjectEntity getAccessibleProject(UUID projectId, UUID userId) {
        return moveProjectRepository.findAccessibleById(projectId, userId).orElseThrow(() -> new MoveProjectNotFoundException("Проект переезда не найден"));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public MoveProjectEntity getAccessibleProjectForUpdate(UUID projectId, UUID userId) {
        var project = lockAccessibleProject(projectId, userId);
        if (project.getArchivedAt() != null) {
            throw new ResourceConflictException("Переезд находится в архиве. Восстановите его для изменения данных.");
        }
        return project;
    }

    /** Archive/restore also take this lock, but must be allowed to load archived projects. */
    @Transactional(propagation = Propagation.MANDATORY)
    public MoveProjectEntity lockAccessibleProject(UUID projectId, UUID userId) {
        return moveProjectRepository.findForUpdate(projectId, userId)
                .orElseThrow(() -> new MoveProjectNotFoundException("Проект переезда не найден"));
    }
}
