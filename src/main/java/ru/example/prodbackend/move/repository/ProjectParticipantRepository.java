package ru.example.prodbackend.move.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.example.prodbackend.move.entity.ProjectParticipantEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectParticipantRepository extends JpaRepository<ProjectParticipantEntity, UUID> {
    @EntityGraph(attributePaths = "user")
    List<ProjectParticipantEntity> findAllByProject_IdOrderByUser_IdAsc(UUID projectId);

    @EntityGraph(attributePaths = "user")
    Optional<ProjectParticipantEntity> findByProject_IdAndUser_Id(UUID projectId, UUID userId);
}
