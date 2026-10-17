package ru.example.prodbackend.event.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.example.prodbackend.event.entity.EventObjectType;
import ru.example.prodbackend.event.entity.ProjectEventEntity;
import ru.example.prodbackend.event.entity.ProjectEventType;
import ru.example.prodbackend.event.repository.ProjectEventRepository;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import ru.example.prodbackend.user.entity.UserEntity;
import ru.example.prodbackend.common.dto.PageResponse;
import ru.example.prodbackend.common.dto.PaginationRequest;
import ru.example.prodbackend.event.dto.ProjectEventResponse;
import ru.example.prodbackend.move.service.ProjectAccessService;
import ru.example.prodbackend.box.entity.BoxEntity;
import ru.example.prodbackend.box.entity.BoxStatus;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectEventService {

    private final ProjectEventRepository projectEventRepository;
    private final ProjectAccessService projectAccessService;

    @Transactional(readOnly = true)
    public PageResponse<ProjectEventResponse> findAll(UUID projectId, PaginationRequest pagination, UUID userId) {
        projectAccessService.getOwnedProject(projectId, userId);
        var pageable = PageRequest.of(pagination.getPage(), pagination.getSize(), Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")));
        return PageResponse.from(projectEventRepository.findAllByProject_Id(projectId, pageable).map(this::toResponse));
    }

    /** Вызывается из бизнес-сервиса после проверки доступа и только в транзакции основного действия. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(MoveProjectEntity project, UserEntity actor, ProjectEventType eventType, EventObjectType objectType, UUID objectId, String description) {
        projectEventRepository.save(ProjectEventEntity.builder().project(project).actor(actor).eventType(eventType)
                .objectType(objectType).objectId(objectId).description(description).build());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordStatusCorrection(BoxEntity box, BoxStatus previousStatus, BoxStatus newStatus, String description) {
        projectEventRepository.save(ProjectEventEntity.builder().project(box.getProject()).actor(box.getProject().getOwner())
                .eventType(ProjectEventType.BOX_STATUS_CORRECTED).objectType(EventObjectType.BOX).objectId(box.getId())
                .previousStatus(previousStatus).newStatus(newStatus).description(description).build());
    }

    private ProjectEventResponse toResponse(ProjectEventEntity event) {
        return ProjectEventResponse.builder().id(event.getId()).projectId(event.getProject().getId())
                .actorId(event.getActor() == null ? null : event.getActor().getId()).eventType(event.getEventType())
                .objectType(event.getObjectType()).objectId(event.getObjectId()).description(event.getDescription()).occurredAt(event.getOccurredAt())
                .previousStatus(event.getPreviousStatus()).newStatus(event.getNewStatus()).build();
    }
}
