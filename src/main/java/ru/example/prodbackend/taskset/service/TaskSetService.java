package ru.example.prodbackend.taskset.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.example.prodbackend.common.dto.PageResponse;
import ru.example.prodbackend.common.dto.PaginationRequest;
import ru.example.prodbackend.exception.TaskSetNotFoundException;
import ru.example.prodbackend.exception.TaskFolderNotFoundException;
import ru.example.prodbackend.exception.TaskSetAlreadyAppliedException;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import ru.example.prodbackend.move.service.ProjectAccessService;
import ru.example.prodbackend.task.entity.MoveTaskEntity;
import ru.example.prodbackend.task.repository.MoveTaskRepository;
import ru.example.prodbackend.task.service.MoveTaskMapper;
import ru.example.prodbackend.task.service.TaskDeadline;
import ru.example.prodbackend.taskset.dto.*;
import ru.example.prodbackend.taskset.entity.*;
import ru.example.prodbackend.taskset.repository.*;
import ru.example.prodbackend.user.repository.UserRepository;
import ru.example.prodbackend.taskfolder.repository.TaskFolderRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskSetService {
    private final TaskSetRepository sets;
    private final TaskSetApplicationRepository applications;
    private final MoveTaskRepository tasks;
    private final UserRepository users;
    private final ProjectAccessService projectAccess;
    private final TaskFolderRepository folders;

    @Transactional
    public TaskSetResponse create(UUID userId, SaveTaskSetRequest request) {
        var set = TaskSetEntity.builder().owner(users.getReferenceById(userId)).name(request.name().strip())
                .description(normalize(request.description())).build();
        fillItems(set, request.items());
        return toResponse(sets.saveAndFlush(set));
    }

    public PageResponse<TaskSetSummaryResponse> findAll(UUID userId, PaginationRequest pagination) {
        var page = PageRequest.of(pagination.getPage(), pagination.getSize(), Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return PageResponse.from(sets.findAllByOwner_IdAndDeletedAtIsNull(userId, page).map(set ->
                new TaskSetSummaryResponse(set.getId(), set.getName(), set.getDescription(), set.getCreatedAt(), set.getUpdatedAt())));
    }

    public TaskSetResponse findById(UUID userId, UUID setId) {
        return toResponse(sets.findByIdAndOwner_IdAndDeletedAtIsNull(setId, userId).orElseThrow(TaskSetNotFoundException::new));
    }

    @Transactional
    public TaskSetResponse replace(UUID userId, UUID setId, SaveTaskSetRequest request) {
        var set = lockOwned(setId, userId);
        requireActive(set);
        // Сначала удалить старые позиции: вставки Hibernate иначе могут опередить orphanRemoval.
        set.getItems().clear();
        sets.flush();
        set.setName(request.name().strip());
        set.setDescription(normalize(request.description()));
        set.setUpdatedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        fillItems(set, request.items());
        return toResponse(sets.saveAndFlush(set));
    }

    @Transactional
    public void delete(UUID userId, UUID setId) {
        var set = lockOwned(setId, userId);
        requireActive(set);
        set.setDeletedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        sets.flush();
    }

    @Transactional
    public TaskSetApplicationResponse apply(UUID userId, UUID projectId, UUID setId, ApplyTaskSetRequest request) {
        var project = projectAccess.getAccessibleProjectForUpdate(projectId, userId);
        return toApplicationResponse(applyLocked(project, setId, userId,
                request == null ? null : request.getFolderId(), request != null && request.isFolderIdPresent()));
    }

    /** Только при создании проекта в уже открытой транзакции. Порядок: проект → наборы по UUID. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void applyInitialSets(MoveProjectEntity project, List<UUID> setIds, UUID userId) {
        for (UUID setId : setIds.stream().distinct().sorted().toList()) applyLocked(project, setId, userId, null, false);
    }

    private TaskSetApplicationEntity applyLocked(MoveProjectEntity project, UUID setId, UUID userId, UUID folderId, boolean folderIdPresent) {
        var set = lockOwned(setId, userId);
        var existing = applications.findByProject_IdAndTaskSet_Id(project.getId(), setId);
        if (existing.isPresent()) {
            // Compare provenance before looking up a folder: it may have been deleted legitimately.
            if (folderIdPresent && !Objects.equals(existing.get().getFolderId(), folderId)) throw new TaskSetAlreadyAppliedException();
            return existing.get(); // Повтор допустим и после мягкого удаления набора.
        }
        requireActive(set);
        var folder = folderId == null ? null : folders.findByIdAndProject_Id(folderId, project.getId())
                .orElseThrow(TaskFolderNotFoundException::new);
        var deadlines = set.getItems().stream().map(item -> TaskDeadline.relative(project.getMoveDate(), item.getOffsetDays())).toList();
        var application = applications.saveAndFlush(TaskSetApplicationEntity.builder().project(project).taskSet(set)
                .taskSetName(set.getName()).folderId(folderId).build());
        for (int i = 0; i < set.getItems().size(); i++) {
            var item = set.getItems().get(i);
            var task = MoveTaskEntity.builder().project(project).folder(folder).title(item.getTitle()).description(item.getDescription()).stage(item.getStage())
                    .taskSetApplication(application).taskSetPosition(item.getPosition()).build();
            deadlines.get(i).applyTo(task);
            tasks.save(task);
        }
        tasks.flush();
        return application;
    }

    private TaskSetApplicationResponse toApplicationResponse(TaskSetApplicationEntity application) {
        var copied = tasks.findAllByTaskSetApplication_IdOrderByTaskSetPositionAscIdAsc(application.getId()).stream().map(MoveTaskMapper::toResponse).toList();
        return new TaskSetApplicationResponse(application.getId(), application.getProject().getId(), application.getTaskSet().getId(),
                application.getTaskSetName(), application.getFolderId(), application.getAppliedAt(), copied);
    }

    private TaskSetEntity lockOwned(UUID setId, UUID userId) { return sets.lockOwned(setId, userId).orElseThrow(TaskSetNotFoundException::new); }
    private void requireActive(TaskSetEntity set) { if (set.getDeletedAt() != null) throw new TaskSetNotFoundException(); }
    private String normalize(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    private void fillItems(TaskSetEntity set, List<TaskSetItemRequest> items) {
        for (int position = 0; position < items.size(); position++) {
            var item = items.get(position);
            set.getItems().add(TaskSetItemEntity.builder().taskSet(set).title(item.title().strip()).description(normalize(item.description()))
                    .stage(item.stage()).offsetDays(item.offsetDays()).position(position).build());
        }
    }
    private TaskSetResponse toResponse(TaskSetEntity set) {
        var items = set.getItems().stream().map(item -> new TaskSetItemResponse(item.getId(), item.getTitle(), item.getDescription(),
                item.getStage(), item.getOffsetDays(), item.getPosition())).toList();
        return new TaskSetResponse(set.getId(), set.getName(), set.getDescription(), items, set.getCreatedAt(), set.getUpdatedAt());
    }
}
