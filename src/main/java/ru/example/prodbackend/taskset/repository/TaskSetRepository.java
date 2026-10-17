package ru.example.prodbackend.taskset.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import ru.example.prodbackend.taskset.entity.TaskSetEntity;
import java.util.Optional;
import java.util.UUID;

public interface TaskSetRepository extends JpaRepository<TaskSetEntity, UUID> {
    Page<TaskSetEntity> findAllByOwner_IdAndDeletedAtIsNull(UUID userId, Pageable pageable);
    @EntityGraph(attributePaths = "items")
    Optional<TaskSetEntity> findByIdAndOwner_IdAndDeletedAtIsNull(UUID setId, UUID userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TaskSetEntity s where s.id = :setId and s.owner.id = :userId")
    Optional<TaskSetEntity> lockOwned(@Param("setId") UUID setId, @Param("userId") UUID userId);
}
