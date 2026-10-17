package ru.example.prodbackend.task.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.example.prodbackend.exception.RequestValidationException;
import ru.example.prodbackend.move.repository.ProjectParticipantRepository;
import ru.example.prodbackend.task.entity.MoveTaskEntity;

@Service
@RequiredArgsConstructor
public class TaskAssignmentService {
    private final ProjectParticipantRepository participants;

    /** Caller holds the project lock; validation and persistence belong to the same transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void apply(MoveTaskEntity task, UUID assigneeId, boolean idPresent, String freeName) {
        if (idPresent || assigneeId != null) {
            var project = task.getProject();
            var assignee = assigneeId == null ? null : project.getOwner().getId().equals(assigneeId) ? project.getOwner()
                    : participants.findByProject_IdAndUser_Id(project.getId(), assigneeId)
                    .orElseThrow(() -> new RequestValidationException("assigneeId", "Ответственный должен быть участником этого переезда"))
                    .getUser();
            task.setAssignee(assignee);
            task.setAssigneeName(null);
        } else {
            task.setAssignee(null);
            task.setAssigneeName(freeName == null || freeName.isBlank() ? null : freeName.strip());
        }
    }
}
