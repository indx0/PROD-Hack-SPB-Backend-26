package ru.example.prodbackend.taskset.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import ru.example.prodbackend.taskset.entity.TaskSetApplicationEntity;
import java.util.Optional;
import java.util.UUID;

public interface TaskSetApplicationRepository extends JpaRepository<TaskSetApplicationEntity, UUID> {
    Optional<TaskSetApplicationEntity> findByProject_IdAndTaskSet_Id(UUID projectId, UUID setId);

    @EntityGraph(attributePaths = {"project", "taskSet"})
    Page<TaskSetApplicationEntity> findAllByProject_Id(UUID projectId, Pageable pageable);
}
