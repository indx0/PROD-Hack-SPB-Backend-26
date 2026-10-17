package ru.example.prodbackend.task.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import ru.example.prodbackend.task.entity.MoveTaskEntity;
import ru.example.prodbackend.task.entity.TaskStage;

import java.util.Optional;
import java.util.List;
import java.util.Collection;
import java.util.UUID;

public interface MoveTaskRepository extends JpaRepository<MoveTaskEntity, UUID> {

    // Все изменения задач сначала блокируют проект, в том числе перенос сроков.
    @EntityGraph(attributePaths = "template")
    List<MoveTaskEntity> findAllByProject_IdAndIdIn(UUID projectId, Collection<UUID> ids);

    long countByProject_Id(UUID projectId);

    long countByProject_IdAndCompletedAtIsNotNull(UUID projectId);

    @EntityGraph(attributePaths = "template")
    Optional<MoveTaskEntity> findByIdAndProject_Id(UUID taskId, UUID projectId);

    boolean existsByProject_IdAndTemplate_Id(UUID projectId, UUID templateId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from MoveTaskEntity t where t.id = :taskId and t.project.id = :projectId")
    Optional<MoveTaskEntity> findForUpdate(@Param("taskId") UUID taskId, @Param("projectId") UUID projectId);

    @EntityGraph(attributePaths = "template")
    @Query("""
            select t from MoveTaskEntity t
            where t.project.id = :projectId
              and (:stage is null or t.stage = :stage)
              and (
                  :completed is null
                  or (:completed = true and t.completedAt is not null)
                  or (:completed = false and t.completedAt is null)
              )
            """)
    Page<MoveTaskEntity> findAllByFilters(
            @Param("projectId") UUID projectId,
            @Param("stage") TaskStage stage,
            @Param("completed") Boolean completed,
            Pageable pageable);
}
