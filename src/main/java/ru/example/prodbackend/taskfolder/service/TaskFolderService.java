package ru.example.prodbackend.taskfolder.service;

import lombok.RequiredArgsConstructor;
import lombok.Getter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
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

    @Getter
    @RequiredArgsConstructor
    public static class CreateResult {

        private final TaskFolderResponse folder;
        private final boolean created;
    }

    @Transactional
    public CreateResult create(UUID projectId, CreateTaskFolderRequest request, UUID userId) {
        projectAccess.getAccessibleProjectForUpdate(projectId, userId);
        if (request.getId() == null) throw new RequestValidationException("id", "UUID папки обязателен");
        var name = normalizeName(request.getName());
        var color = validateColor(request.getColorKey());
        boolean created = folders.insertIfAbsent(request.getId(), projectId, name, color,
                Instant.now().truncatedTo(ChronoUnit.MICROS)) == 1;
        var folder = folders.findById(request.getId()).orElseThrow(TaskFolderNotFoundException::new);
        if (!folder.getProject().getId().equals(projectId)
                || !folder.getInitialName().equals(name) || !folder.getInitialColorKey().equals(color)
                || !folder.getName().equals(name) || !folder.getColorKey().equals(color)) {
            throw new ResourceConflictException("UUID папки уже занят или папка изменена после создания");
        }
        return new CreateResult(toResponse(folder), created);
    }

    /** Only for a new project within its creation transaction; UUID duplicates are validated by the caller. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void createInitialFolders(MoveProjectEntity project, List<CreateTaskFolderRequest> requests) {
        if (requests.isEmpty()) return;
        folders.flush(); // Persist the project and its participants before the native inserts.
        for (int i = 0; i < requests.size(); i++) {
            var request = requests.get(i);
            String prefix = "folders[" + i + "].";
            if (request.getId() == null) throw new RequestValidationException(prefix + "id", "UUID папки обязателен");
            var name = normalizeName(request.getName(), prefix + "name");
            var color = validateColor(request.getColorKey(), prefix + "colorKey");
            if (folders.insertIfAbsent(request.getId(), project.getId(), name, color, Instant.now().truncatedTo(ChronoUnit.MICROS)) != 1) {
                throw new ResourceConflictException("UUID создаваемой папки уже занят");
            }
        }
    }

    public PageResponse<TaskFolderResponse> findAll(UUID projectId, PaginationRequest pagination, UUID userId) {
        projectAccess.getAccessibleProject(projectId, userId);
        var page = folders.findAllByProject_Id(projectId,
                PageRequest.of(pagination.getPage(), pagination.getSize(), Sort.by("createdAt", "id")));
        var counts = page.isEmpty() ? Map.<UUID, TaskFolderCounts>of()
                : tasks.countByFolderIds(page.getContent().stream().map(TaskFolderEntity::getId).toList()).stream()
                    .collect(Collectors.toMap(TaskFolderCounts::getFolderId, Function.identity()));
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
        return normalizeName(name, "name");
    }

    private String normalizeName(String name, String field) {
        var normalized = name == null ? "" : name.strip();
        if (normalized.isBlank() || normalized.codePointCount(0, normalized.length()) > 80) {
            throw new RequestValidationException(field, "Название папки должно содержать от 1 до 80 символов после удаления пробелов по краям");
        }
        return normalized;
    }

    private String validateColor(String color) {
        return validateColor(color, "colorKey");
    }

    private String validateColor(String color, String field) {
        if (color == null || !COLORS.contains(color)) {
            throw new RequestValidationException(field, "Допустимые цвета: mint, cyan, coral, sand, lilac, forest");
        }
        return color;
    }

    private TaskFolderResponse toResponse(TaskFolderEntity folder) {
        var counts = tasks.countByFolderIds(List.of(folder.getId()));
        return toResponse(folder, counts.isEmpty() ? null : counts.get(0));
    }

    private TaskFolderResponse toResponse(TaskFolderEntity folder, TaskFolderCounts counts) {
        return new TaskFolderResponse(folder.getId(), folder.getProject().getId(), folder.getName(), folder.getColorKey(),
                counts == null ? 0 : counts.getTaskCount(), counts == null ? 0 : counts.getCompletedTaskCount(),
                folder.getCreatedAt(), folder.getUpdatedAt());
    }
}
