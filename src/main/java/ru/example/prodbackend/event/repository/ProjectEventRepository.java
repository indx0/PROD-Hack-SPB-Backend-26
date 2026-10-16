package ru.example.prodbackend.event.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import ru.example.prodbackend.event.entity.ProjectEventEntity;

import java.util.UUID;

public interface ProjectEventRepository extends JpaRepository<ProjectEventEntity, UUID> {

    @EntityGraph(attributePaths = "actor")
    Page<ProjectEventEntity> findAllByProject_Id(UUID projectId, Pageable pageable);
}
