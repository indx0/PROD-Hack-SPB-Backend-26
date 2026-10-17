package ru.example.prodbackend.move.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.example.prodbackend.exception.RequestValidationException;
import ru.example.prodbackend.exception.ResourceConflictException;
import ru.example.prodbackend.task.repository.MoveTaskRepository;
import ru.example.prodbackend.exception.UserNotFoundException;
import ru.example.prodbackend.move.dto.request.AddProjectParticipantRequest;
import ru.example.prodbackend.move.dto.response.ProjectParticipantResponse;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import ru.example.prodbackend.move.entity.ProjectParticipantEntity;
import ru.example.prodbackend.move.repository.ProjectParticipantRepository;
import ru.example.prodbackend.user.entity.UserEntity;
import ru.example.prodbackend.user.repository.UserRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectParticipantService {
    private final ProjectParticipantRepository participantRepository;
    private final ProjectAccessService projectAccessService;
    private final UserRepository userRepository;
    private final MoveTaskRepository moveTaskRepository;

    public List<ProjectParticipantResponse> findAll(UUID projectId, UUID userId) {
        var project = projectAccessService.getAccessibleProject(projectId, userId);
        var result = new ArrayList<ProjectParticipantResponse>();
        result.add(toResponse(project.getOwner(), true, project.getCreatedAt()));
        participantRepository.findAllByProject_IdOrderByUser_IdAsc(projectId).stream()
                .filter(participant -> !participant.getUser().getId().equals(project.getOwner().getId()))
                .map(participant -> toResponse(participant.getUser(), false, participant.getAddedAt())).forEach(result::add);
        return result;
    }

    @Transactional
    public ProjectParticipantResponse add(UUID projectId, UUID actorId, AddProjectParticipantRequest request) {
        var project = projectAccessService.getAccessibleProjectForUpdate(projectId, actorId);
        var user = resolveUser(request);
        if (project.getOwner().getId().equals(user.getId())) return toResponse(user, true, project.getCreatedAt());
        var participant = participantRepository.findByProject_IdAndUser_Id(projectId, user.getId())
                .orElseGet(() -> participantRepository.saveAndFlush(ProjectParticipantEntity.builder().project(project).user(user).build()));
        return toResponse(user, false, participant.getAddedAt());
    }

    @Transactional
    public void remove(UUID projectId, UUID actorId, UUID userId) {
        var project = projectAccessService.getAccessibleProjectForUpdate(projectId, actorId);
        if (project.getOwner().getId().equals(userId)) {
            throw new ResourceConflictException("Нельзя отозвать доступ создателя переезда");
        }
        moveTaskRepository.clearAssignments(projectId, userId, Instant.now());
        participantRepository.findByProject_IdAndUser_Id(projectId, userId).ifPresent(participantRepository::delete);
        participantRepository.flush();
    }

    /** Resolve before creating the project; an unavailable account aborts the whole transaction. */
    public List<UserEntity> resolveUsers(List<AddProjectParticipantRequest> requests) {
        var users = new LinkedHashMap<UUID, UserEntity>();
        for (var request : requests) {
            var user = resolveUser(request);
            users.putIfAbsent(user.getId(), user);
        }
        return List.copyOf(users.values());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void addInitialParticipants(MoveProjectEntity project, List<UserEntity> users) {
        participantRepository.saveAll(users.stream().filter(user -> !user.getId().equals(project.getOwner().getId()))
                .map(user -> ProjectParticipantEntity.builder().project(project).user(user).build()).toList());
    }

    private UserEntity resolveUser(AddProjectParticipantRequest request) {
        if (request == null || !request.isExactlyOneSelectorPresent()) {
            throw new RequestValidationException("participants", "Укажите ровно одно поле: userId или email");
        }
        return (request.getUserId() != null ? userRepository.findById(request.getUserId()) : userRepository.findByEmail(request.getEmail().strip()))
                .orElseThrow(() -> new UserNotFoundException("Пользователь не найден; добавлять можно только существующие аккаунты"));
    }

    private ProjectParticipantResponse toResponse(UserEntity user, boolean creator, Instant addedAt) {
        return ProjectParticipantResponse.builder().userId(user.getId()).email(user.getEmail()).firstName(user.getFirstName())
                .lastName(user.getLastName()).creator(creator).addedAt(addedAt).build();
    }
}
