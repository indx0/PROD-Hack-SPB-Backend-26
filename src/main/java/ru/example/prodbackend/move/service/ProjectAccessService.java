package ru.example.prodbackend.move.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.example.prodbackend.exception.MoveProjectNotFoundException;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import ru.example.prodbackend.move.repository.MoveProjectRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectAccessService {

    private final MoveProjectRepository moveProjectRepository;

    public MoveProjectEntity getOwnedProject(UUID projectId, UUID userId) {
        return moveProjectRepository.findByIdAndOwner_Id(projectId, userId).orElseThrow(() -> new MoveProjectNotFoundException("Проект переезда не найден"));
    }
}
