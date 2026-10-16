package ru.example.prodbackend.event.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.example.prodbackend.event.entity.ProjectEventEntity;

import java.util.UUID;

public interface ProjectEventRepository extends JpaRepository<ProjectEventEntity, UUID> {
}
