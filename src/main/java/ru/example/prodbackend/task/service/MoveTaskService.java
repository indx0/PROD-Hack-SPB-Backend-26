package ru.example.prodbackend.task.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.example.prodbackend.common.dto.PageResponse;
import ru.example.prodbackend.common.dto.PaginationRequest;
import ru.example.prodbackend.event.entity.EventObjectType;
import ru.example.prodbackend.event.entity.ProjectEventType;
import ru.example.prodbackend.event.service.ProjectEventService;
import ru.example.prodbackend.exception.MoveTaskNotFoundException;
import ru.example.prodbackend.exception.RequestValidationException;
import ru.example.prodbackend.exception.ResourceConflictException;
import ru.example.prodbackend.exception.TaskTemplateNotFoundException;
import ru.example.prodbackend.move.service.ProjectAccessService;
import ru.example.prodbackend.task.dto.request.*;
import ru.example.prodbackend.task.dto.response.MoveTaskResponse;
import ru.example.prodbackend.task.dto.response.TaskTemplateResponse;
import ru.example.prodbackend.task.dto.response.TaskSummaryResponse;
import ru.example.prodbackend.task.entity.MoveTaskEntity;
import ru.example.prodbackend.task.entity.TaskStage;
import ru.example.prodbackend.task.entity.TaskTemplateEntity;
import ru.example.prodbackend.task.repository.MoveTaskRepository;
import ru.example.prodbackend.task.repository.TaskTemplateRepository;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MoveTaskService {

    private final MoveTaskRepository moveTaskRepository;
    private final TaskTemplateRepository taskTemplateRepository;
    private final ProjectAccessService projectAccessService;
    private final ProjectEventService projectEventService;

    public TaskSummaryResponse summary(UUID projectId, UUID userId) {
        projectAccessService.getOwnedProject(projectId, userId);
        return TaskSummaryResponse.builder()
                .totalTasks(moveTaskRepository.countByProject_Id(projectId))
                .completedTasks(moveTaskRepository.countByProject_IdAndCompletedAtIsNotNull(projectId))
                .build();
    }

    @Transactional
    public MoveTaskResponse create(UUID projectId, CreateMoveTaskRequest request, UUID userId) {
        var project = projectAccessService.getOwnedProject(projectId, userId);

        MoveTaskEntity task = MoveTaskEntity.builder()
                .project(project)
                .title(normalizeTitle(request.getTitle()))
                .description(normalizeOptional(request.getDescription()))
                .stage(request.getStage())
                .dueDate(request.getDueDate())
                .assigneeName(normalizeOptional(request.getAssigneeName()))
                .build();

        return toResponse(moveTaskRepository.saveAndFlush(task));
    }

    public PageResponse<MoveTaskResponse> findAll(UUID projectId, MoveTaskFilter filter, PaginationRequest pagination, UUID userId) {
        projectAccessService.getOwnedProject(projectId, userId);
        var pageable = PageRequest.of(pagination.getPage(), pagination.getSize(), Sort.by("createdAt", "id"));
        var tasks = moveTaskRepository.findAllByFilters(projectId, filter.getStage(), filter.getCompleted(), pageable);
        return PageResponse.from(tasks.map(this::toResponse));
    }

    public MoveTaskResponse findById(UUID projectId, UUID taskId, UUID userId) {
        projectAccessService.getOwnedProject(projectId, userId);
        var task = moveTaskRepository.findByIdAndProject_Id(taskId, projectId).orElseThrow(() -> new MoveTaskNotFoundException("move task not found"));
        return toResponse(task);
    }

    @Transactional
    public MoveTaskResponse update(UUID projectId, UUID taskId, UpdateMoveTaskRequest request, UUID userId) {
        MoveTaskEntity task = requireTaskForUpdate(projectId, taskId, userId);

        if (!request.isUpdatePresent()) {
            throw new RequestValidationException("request", "Укажите хотя бы одно поле для изменения");
        }
        if (request.getTitle() != null) {
            task.setTitle(normalizeTitle(request.getTitle()));
        }
        if (request.getStage() != null) {
            task.setStage(request.getStage());
        }
        if (request.isDescriptionPresent()) {
            task.setDescription(normalizeOptional(request.getDescription()));
        }
        if (request.isDueDatePresent()) {
            task.setDueDate(request.getDueDate());
        }
        if (request.isAssigneeNamePresent()) {
            task.setAssigneeName(normalizeOptional(request.getAssigneeName()));
        }

        return toResponse(moveTaskRepository.saveAndFlush(task));
    }

    @Transactional
    public MoveTaskResponse changeCompletion(UUID projectId, UUID taskId, boolean completed, UUID userId) {
        MoveTaskEntity task = requireTaskForUpdate(projectId, taskId, userId);

        if ((task.getCompletedAt() != null) == completed) {
            return toResponse(task);
        }

        task.setCompletedAt(completed ? Instant.now() : null);
        moveTaskRepository.saveAndFlush(task);

        if (completed) {
            projectEventService.record(task.getProject(), task.getProject().getOwner(), ProjectEventType.TASK_COMPLETED,
                    EventObjectType.TASK, task.getId(), "Выполнена задача: " + task.getTitle());
        }

        return toResponse(task);
    }

    @Transactional
    public void delete(UUID projectId, UUID taskId, UUID userId) {
        MoveTaskEntity task = requireTaskForUpdate(projectId, taskId, userId);
        moveTaskRepository.delete(task);
        moveTaskRepository.flush();
    }

    public PageResponse<TaskTemplateResponse> findTemplates(UUID projectId, TaskStage stage, PaginationRequest pagination, UUID userId) {
        projectAccessService.getOwnedProject(projectId, userId);
        var pageable = PageRequest.of(pagination.getPage(), pagination.getSize(), Sort.by("title", "id"));
        return PageResponse.from(taskTemplateRepository.findAllByStageFilter(stage, pageable).map(this::toTemplateResponse));
    }

    @Transactional
    public MoveTaskResponse applyTemplate(UUID projectId, ApplyTaskTemplateRequest request, UUID userId) {
        var project = projectAccessService.getOwnedProject(projectId, userId);
        var template = taskTemplateRepository.findById(request.getTemplateId()).orElseThrow(() -> new TaskTemplateNotFoundException("task template not found exception"));

        if (moveTaskRepository.existsByProject_IdAndTemplate_Id(projectId, template.getId())) {
            throw new ResourceConflictException("Задача из этого шаблона уже существует в проекте");
        }

        MoveTaskEntity task = MoveTaskEntity.builder()
                .project(project)
                .template(template)
                .title(template.getTitle())
                .description(template.getDescription())
                .stage(template.getStage())
                .dueDate(request.getDueDate())
                .assigneeName(normalizeOptional(request.getAssigneeName()))
                .build();

        return toResponse(moveTaskRepository.saveAndFlush(task));
    }

    private MoveTaskEntity requireTaskForUpdate(UUID projectId, UUID taskId, UUID userId) {
        projectAccessService.getOwnedProject(projectId, userId);
        return moveTaskRepository.findForUpdate(taskId, projectId).orElseThrow(() -> new MoveTaskNotFoundException("move task not found"));
    }

    private String normalizeTitle(String title) {
        String normalized = title == null ? "" : title.strip();

        if (normalized.isBlank()) {
            throw new RequestValidationException("title", "Название задачи не должно быть пустым");
        }

        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isBlank() ? null : normalized;
    }

    private MoveTaskResponse toResponse(MoveTaskEntity task) {
        return MoveTaskResponse.builder()
                .id(task.getId())
                .projectId(task.getProject().getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .stage(task.getStage())
                .dueDate(task.getDueDate())
                .assigneeName(task.getAssigneeName())
                .completed(task.getCompletedAt() != null)
                .completedAt(task.getCompletedAt())
                .templateId(task.getTemplate() == null ? null : task.getTemplate().getId())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    private TaskTemplateResponse toTemplateResponse(TaskTemplateEntity template) {
        return TaskTemplateResponse.builder()
                .id(template.getId())
                .title(template.getTitle())
                .description(template.getDescription())
                .stage(template.getStage())
                .dueDateOffsetDays(template.getDueDateOffsetDays())
                .build();
    }
}
