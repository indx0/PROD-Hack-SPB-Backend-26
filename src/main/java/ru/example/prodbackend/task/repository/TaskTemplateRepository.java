package ru.example.prodbackend.task.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import ru.example.prodbackend.task.entity.TaskStage;
import ru.example.prodbackend.task.entity.TaskTemplateEntity;

import java.util.UUID;
import java.util.Optional;

public interface TaskTemplateRepository extends JpaRepository<TaskTemplateEntity, UUID> {

    Optional<TaskTemplateEntity> findByStageAndTitleAndOwnerIsNull(TaskStage stage, String title);

    @Query("""
            select t from TaskTemplateEntity t
            where t.deletedAt is null and (t.owner is null or t.owner.id = :userId)
            and (:stage is null or t.stage = :stage)
            """)
    Page<TaskTemplateEntity> findAllByStageFilter(@Param("userId") UUID userId, @Param("stage") TaskStage stage, Pageable pageable);

    @Query("select t from TaskTemplateEntity t where t.id = :id and t.deletedAt is null and (t.owner is null or t.owner.id = :userId)")
    Optional<TaskTemplateEntity> findAccessible(@Param("id") UUID id, @Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TaskTemplateEntity t where t.id = :id and t.deletedAt is null and (t.owner is null or t.owner.id = :userId)")
    Optional<TaskTemplateEntity> findAccessibleForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TaskTemplateEntity t where t.id = :id and t.owner.id = :userId and t.deletedAt is null")
    Optional<TaskTemplateEntity> findOwnedForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);
}
