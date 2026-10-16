package ru.example.prodbackend.move.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.example.prodbackend.common.dto.PageResponse;
import ru.example.prodbackend.common.dto.PaginationRequest;
import ru.example.prodbackend.exception.UserNotFoundException;
import ru.example.prodbackend.move.dto.request.CreateMoveProjectRequest;
import ru.example.prodbackend.move.dto.request.UpdateMoveProjectRequest;
import ru.example.prodbackend.move.dto.response.MoveProjectResponse;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import ru.example.prodbackend.move.repository.MoveProjectRepository;
import ru.example.prodbackend.user.entity.UserEntity;
import ru.example.prodbackend.user.repository.UserRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MoveProjectService {

    private final MoveProjectRepository moveProjectRepository;
    private final UserRepository userRepository;
    private final ProjectAccessService projectAccessService;

    @Transactional
    public MoveProjectResponse createMoveProject(CreateMoveProjectRequest request, UserEntity user) {
        UserEntity owner = userRepository.findById(user.getId()).orElseThrow(() -> new UserNotFoundException("Current user not found"));

        MoveProjectEntity project = MoveProjectEntity.builder()
                .owner(owner)
                .name(request.getName().strip())
                .moveDate(request.getMoveDate())
                .originAddress(normalizeAddress(request.getOriginAddress()))
                .destinationAddress(normalizeAddress(request.getDestinationAddress()))
                .build();

        MoveProjectEntity savedProject = moveProjectRepository.save(project);

        return toResponse(savedProject);
    }

    public PageResponse<MoveProjectResponse> getMoveProjects(PaginationRequest request, UserEntity user) {
        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        PageRequest pageable = PageRequest.of(request.getPage(), request.getSize(), sort);
        Page<MoveProjectEntity> projects = moveProjectRepository.findAllByOwner_Id(user.getId(), pageable);

        return PageResponse.from(projects.map(this::toResponse));
    }

    public MoveProjectResponse getMoveProject(UUID projectId, UserEntity user) {
        return toResponse(projectAccessService.getOwnedProject(projectId, user.getId()));
    }

    @Transactional
    public MoveProjectResponse updateMoveProject(UUID projectId, UpdateMoveProjectRequest request, UserEntity user) {
        MoveProjectEntity project = projectAccessService.getOwnedProject(projectId, user.getId());

        if (request.getName() != null) {
            project.setName(request.getName().strip());
        }

        if (request.getMoveDate() != null) {
            project.setMoveDate(request.getMoveDate());
        }

        if (request.getOriginAddress() != null) {
            project.setOriginAddress(normalizeAddress(request.getOriginAddress()));
        }

        if (request.getDestinationAddress() != null) {
            project.setDestinationAddress(normalizeAddress(request.getDestinationAddress()));
        }

        MoveProjectEntity savedProject = moveProjectRepository.saveAndFlush(project);
        return toResponse(savedProject);
    }

    @Transactional
    public void deleteMoveProject(UUID projectId, UserEntity user) {
        MoveProjectEntity project = projectAccessService.getOwnedProject(projectId, user.getId());

        // Порядок важен из-за внешних ключей.
        moveProjectRepository.deleteProjectItems(projectId);
        moveProjectRepository.deleteProjectBoxes(projectId);
        moveProjectRepository.deleteProjectRooms(projectId);
        moveProjectRepository.deleteProjectTasks(projectId);
        moveProjectRepository.deleteProjectChecklist(projectId);
        moveProjectRepository.deleteProjectEvents(projectId);

        moveProjectRepository.delete(project);
        moveProjectRepository.flush();
    }

    private MoveProjectResponse toResponse(MoveProjectEntity project) {
        return MoveProjectResponse.builder()
                .id(project.getId())
                .name(project.getName())
                .moveDate(project.getMoveDate())
                .originAddress(project.getOriginAddress())
                .destinationAddress(project.getDestinationAddress())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }

    private String normalizeAddress(String address) {
        if (address == null) {
            return null;
        }

        String normalized = address.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
