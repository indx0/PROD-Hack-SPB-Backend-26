package ru.example.prodbackend.event.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.example.prodbackend.event.entity.EventObjectType;
import ru.example.prodbackend.event.entity.ProjectEventEntity;
import ru.example.prodbackend.event.entity.ProjectEventType;
import ru.example.prodbackend.event.repository.ProjectEventRepository;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import ru.example.prodbackend.user.entity.UserEntity;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectEventService {

    private final ProjectEventRepository projectEventRepository;

    /** Вызывается из бизнес-сервиса после проверки доступа и только в транзакции основного действия. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(MoveProjectEntity project, UserEntity actor, ProjectEventType eventType, EventObjectType objectType, UUID objectId, String description) {
        projectEventRepository.save(ProjectEventEntity.builder().project(project).actor(actor).eventType(eventType)
                .objectType(objectType).objectId(objectId).description(description).build());
    }
}
