package ru.example.prodbackend.task.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import ru.example.prodbackend.task.entity.MoveTaskEntity;
import ru.example.prodbackend.task.entity.TaskStage;
import ru.example.prodbackend.taskfolder.repository.TaskFolderCounts;

import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.Collection;
import java.util.UUID;

public interface MoveTaskRepository extends JpaRepository<MoveTaskEntity, UUID> {

    @Query("""
            select new ru.example.prodbackend.taskfolder.repository.TaskFolderCounts(
                t.folder.id, count(t), sum(case when t.completedAt is not null then 1L else 0L end))
            from MoveTaskEntity t where t.folder.id in :folderIds group by t.folder.id
            """)
    List<TaskFolderCounts> countByFolderIds(@Param("folderIds") Collection<UUID> folderIds);

    // Все изменения задач сначала блокируют проект, в том числе перенос сроков.
    @EntityGraph(attributePaths = {"template", "assignee", "linkedItem", "linkedItem.box", "linkedItem.box.room"})
    List<MoveTaskEntity> findAllByProject_IdAndIdIn(UUID projectId, Collection<UUID> ids);

    @EntityGraph(attributePaths = {"template", "assignee", "linkedItem", "linkedItem.box", "linkedItem.box.room"})
    List<MoveTaskEntity> findAllByTaskSetApplication_IdOrderByTaskSetPositionAscIdAsc(UUID applicationId);

    @EntityGraph(attributePaths = {"template", "assignee", "linkedItem", "linkedItem.box", "linkedItem.box.room"})
    List<MoveTaskEntity> findAllByTaskSetApplication_IdInOrderByTaskSetPositionAscIdAsc(Collection<UUID> applicationIds);

    @Modifying
    @Query("update MoveTaskEntity t set t.assignee = null, t.assigneeName = null, t.updatedAt = :updatedAt where t.project.id = :projectId and t.assignee.id = :userId")
    int clearAssignments(@Param("projectId") UUID projectId, @Param("userId") UUID userId, @Param("updatedAt") Instant updatedAt);

    long countByProject_Id(UUID projectId);

    long countByProject_IdAndCompletedAtIsNotNull(UUID projectId);

    @EntityGraph(attributePaths = {"template", "assignee", "linkedItem", "linkedItem.box", "linkedItem.box.room"})
    Optional<MoveTaskEntity> findByIdAndProject_Id(UUID taskId, UUID projectId);

    boolean existsByProject_IdAndTemplate_Id(UUID projectId, UUID templateId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from MoveTaskEntity t where t.id = :taskId and t.project.id = :projectId")
    Optional<MoveTaskEntity> findForUpdate(@Param("taskId") UUID taskId, @Param("projectId") UUID projectId);

    @EntityGraph(attributePaths = {"template", "assignee", "linkedItem", "linkedItem.box", "linkedItem.box.room"})
    @Query("""
            select t from MoveTaskEntity t
            where t.project.id = :projectId
              and (:stage is null or t.stage = :stage)
              and (:folderId is null or t.folder.id = :folderId)
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
            @Param("folderId") UUID folderId,
            Pageable pageable);
}
