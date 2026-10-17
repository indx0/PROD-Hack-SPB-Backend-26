package ru.example.prodbackend.taskset.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.example.prodbackend.taskset.entity.TaskSetApplicationEntity;
import java.util.Optional;
import java.util.UUID;

public interface TaskSetApplicationRepository extends JpaRepository<TaskSetApplicationEntity, UUID> {
    Optional<TaskSetApplicationEntity> findByProject_IdAndTaskSet_Id(UUID projectId, UUID setId);
}
