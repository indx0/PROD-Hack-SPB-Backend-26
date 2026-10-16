package ru.example.prodbackend.task.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.example.prodbackend.task.entity.TaskStage;
import ru.example.prodbackend.task.entity.TaskTemplateEntity;

import java.util.UUID;
import java.util.Optional;

public interface TaskTemplateRepository extends JpaRepository<TaskTemplateEntity, UUID> {

    boolean existsByStageAndTitle(TaskStage stage, String title);
    Optional<TaskTemplateEntity> findByStageAndTitle(TaskStage stage, String title);

    @Query("""
            select t from TaskTemplateEntity t
            where :stage is null or t.stage = :stage
            """)
    Page<TaskTemplateEntity> findAllByStageFilter(@Param("stage") TaskStage stage, Pageable pageable);
}
