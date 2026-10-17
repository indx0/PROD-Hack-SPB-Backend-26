package ru.example.prodbackend.taskfolder.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.example.prodbackend.common.dto.PageResponse;
import ru.example.prodbackend.common.dto.PaginationRequest;
import ru.example.prodbackend.exception.*;
import ru.example.prodbackend.move.service.ProjectAccessService;
import ru.example.prodbackend.task.repository.MoveTaskRepository;
import ru.example.prodbackend.taskfolder.dto.*;
import ru.example.prodbackend.taskfolder.entity.TaskFolderEntity;
import ru.example.prodbackend.taskfolder.repository.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskFolderService {
    private static final Set<String> COLORS = Set.of("mint", "cyan", "coral", "sand", "lilac", "forest");
    private final TaskFolderRepository folders;
    private final MoveTaskRepository tasks;
    private final ProjectAccessService projectAccess;

    public record CreateResult(TaskFolderResponse folder, boolean created) { }

    @Transactional
    public CreateResult create(UUID projectId, CreateTaskFolderRequest request, UUID userId) {
        projectAccess.getAccessibleProjectForUpdate(projectId, userId);
        if (request.id() == null) throw new RequestValidationException("id", "UUID папки обязателен");
        var name = normalizeName(request.name());
        var color = validateColor(request.colorKey());
        boolean created = folders.insertIfAbsent(request.id(), projectId, name, color,
                Instant.now().truncatedTo(ChronoUnit.MICROS)) == 1;
        var folder = folders.findById(request.id()).orElseThrow(TaskFolderNotFoundException::new);
        if (!folder.getProject().getId().equals(projectId)
                || !folder.getInitialName().equals(name) || !folder.getInitialColorKey().equals(color)
                || !folder.getName().equals(name) || !folder.getColorKey().equals(color)) {
            throw new ResourceConflictException("UUID папки уже занят или папка изменена после создания");
        }
        return new CreateResult(toResponse(folder), created);
    }

    public PageResponse<TaskFolderResponse> findAll(UUID projectId, PaginationRequest pagination, UUID userId) {
        projectAccess.getAccessibleProject(projectId, userId);
        var page = folders.findAllByProject_Id(projectId,
                PageRequest.of(pagination.getPage(), pagination.getSize(), Sort.by("createdAt", "id")));
        var counts = page.isEmpty() ? Map.<UUID, TaskFolderCounts>of()
                : tasks.countByFolderIds(page.getContent().stream().map(TaskFolderEntity::getId).toList()).stream()
                    .collect(Collectors.toMap(TaskFolderCounts::folderId, Function.identity()));
        return PageResponse.from(page.map(folder -> toResponse(folder, counts.get(folder.getId()))));
    }

    public TaskFolderResponse findById(UUID projectId, UUID folderId, UUID userId) {
        projectAccess.getAccessibleProject(projectId, userId);
        return toResponse(requireFolder(projectId, folderId));
    }

    @Transactional
    public TaskFolderResponse update(UUID projectId, UUID folderId, UpdateTaskFolderRequest request, UUID userId) {
        projectAccess.getAccessibleProjectForUpdate(projectId, userId);
        var folder = requireFolder(projectId, folderId);
        // Validate both fields before modifying the managed entity.
        var name = request.isNamePresent() ? normalizeName(request.getName()) : folder.getName();
        var color = request.isColorKeyPresent() ? validateColor(request.getColorKey()) : folder.getColorKey();
        folder.setName(name);
        folder.setColorKey(color);
        folders.flush();
        return toResponse(folder);
    }

    @Transactional
    public void delete(UUID projectId, UUID folderId, UUID userId) {
        projectAccess.getAccessibleProjectForUpdate(projectId, userId);
        var folder = folders.findById(folderId);
        if (folder.isEmpty()) return;
        if (!folder.get().getProject().getId().equals(projectId)) throw new TaskFolderNotFoundException();
        // The FK clears only folder_id in the same transaction, preserving every task and its other fields.
        folders.delete(folder.get());
        folders.flush();
    }

    private TaskFolderEntity requireFolder(UUID projectId, UUID folderId) {
        return folders.findByIdAndProject_Id(folderId, projectId).orElseThrow(TaskFolderNotFoundException::new);
    }

    private String normalizeName(String name) {
        var normalized = name == null ? "" : name.strip();
        if (normalized.isBlank() || normalized.codePointCount(0, normalized.length()) > 80) {
            throw new RequestValidationException("name", "Название папки должно содержать от 1 до 80 символов после удаления пробелов по краям");
        }
        return normalized;
    }

    private String validateColor(String color) {
        if (color == null || !COLORS.contains(color)) {
            throw new RequestValidationException("colorKey", "Допустимые цвета: mint, cyan, coral, sand, lilac, forest");
        }
        return color;
    }

    private TaskFolderResponse toResponse(TaskFolderEntity folder) {
        var counts = tasks.countByFolderIds(List.of(folder.getId()));
        return toResponse(folder, counts.isEmpty() ? null : counts.get(0));
    }

    private TaskFolderResponse toResponse(TaskFolderEntity folder, TaskFolderCounts counts) {
        return new TaskFolderResponse(folder.getId(), folder.getProject().getId(), folder.getName(), folder.getColorKey(),
                counts == null ? 0 : counts.taskCount(), counts == null ? 0 : counts.completedTaskCount(),
                folder.getCreatedAt(), folder.getUpdatedAt());
    }
}
