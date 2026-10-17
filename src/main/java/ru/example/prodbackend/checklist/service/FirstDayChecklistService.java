package ru.example.prodbackend.checklist.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.example.prodbackend.checklist.dto.request.CreateChecklistItemRequest;
import ru.example.prodbackend.checklist.dto.request.UpdateChecklistItemRequest;
import ru.example.prodbackend.checklist.dto.response.ChecklistItemResponse;
import ru.example.prodbackend.checklist.repository.FirstDayChecklistArchiveRepository;
import ru.example.prodbackend.common.dto.PageResponse;
import ru.example.prodbackend.common.dto.PaginationRequest;
import ru.example.prodbackend.exception.FirstDayChecklistRetiredException;
import ru.example.prodbackend.move.service.ProjectAccessService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FirstDayChecklistService {
    private final FirstDayChecklistArchiveRepository archiveRepository;
    private final ProjectAccessService projectAccessService;

    public ChecklistItemResponse create(UUID projectId, CreateChecklistItemRequest request, UUID userId) {
        return retired(projectId, userId);
    }

    public ChecklistItemResponse link(UUID projectId, UUID checklistItemId, UUID linkedItemId, UUID userId) {
        return retired(projectId, userId);
    }

    public ChecklistItemResponse unlink(UUID projectId, UUID checklistItemId, UUID userId) {
        return retired(projectId, userId);
    }

    public ChecklistItemResponse update(UUID projectId, UUID itemId, UpdateChecklistItemRequest request, UUID userId) {
        return retired(projectId, userId);
    }

    public void delete(UUID projectId, UUID itemId, UUID userId) {
        retired(projectId, userId);
    }

    public PageResponse<ChecklistItemResponse> findAll(UUID projectId, PaginationRequest pagination, UUID userId) {
        projectAccessService.getAccessibleProject(projectId, userId);
        var pageable = PageRequest.of(pagination.getPage(), pagination.getSize(), Sort.by("createdAt", "id"));
        return PageResponse.from(archiveRepository.findAll(projectId, pageable));
    }

    private ChecklistItemResponse retired(UUID projectId, UUID userId) {
        projectAccessService.getAccessibleProject(projectId, userId);
        throw new FirstDayChecklistRetiredException();
    }
}
