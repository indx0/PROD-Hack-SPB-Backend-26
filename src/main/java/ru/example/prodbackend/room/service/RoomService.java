package ru.example.prodbackend.room.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.example.prodbackend.box.repository.BoxRepository;
import ru.example.prodbackend.common.dto.PageResponse;
import ru.example.prodbackend.common.dto.PaginationRequest;
import ru.example.prodbackend.exception.RequestValidationException;
import ru.example.prodbackend.exception.ResourceConflictException;
import ru.example.prodbackend.exception.RoomNotFoundException;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import ru.example.prodbackend.move.service.ProjectAccessService;
import ru.example.prodbackend.room.dto.request.CreateRoomRequest;
import ru.example.prodbackend.room.dto.request.UpdateRoomRequest;
import ru.example.prodbackend.room.dto.response.RoomResponse;
import ru.example.prodbackend.room.entity.RoomEntity;
import ru.example.prodbackend.room.repository.RoomRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomService {

    private final RoomRepository roomRepository;
    private final BoxRepository boxRepository;
    private final ProjectAccessService projectAccessService;

    @Transactional
    public RoomResponse createRoom(UUID projectId, CreateRoomRequest request, UUID userId) {
        MoveProjectEntity project = projectAccessService.getAccessibleProjectForUpdate(projectId, userId);
        String name = normalizeName(request.getName());

        if (roomRepository.existsByProject_IdAndName(projectId, name)) {
            throw new ResourceConflictException("Комната с таким названием уже существует в проекте");
        }

        RoomEntity room = RoomEntity.builder()
                .project(project)
                .name(name)
                .iconKey(request.getIconKey())
                .build();

        return toResponse(roomRepository.saveAndFlush(room));
    }

    public PageResponse<RoomResponse> getRooms(UUID projectId, PaginationRequest request, UUID userId) {
        projectAccessService.getAccessibleProject(projectId, userId);

        PageRequest pageable = PageRequest.of(request.getPage(), request.getSize(), Sort.by("name", "id"));
        Page<RoomEntity> rooms = roomRepository.findAllByProject_Id(projectId, pageable);

        return PageResponse.from(rooms.map(this::toResponse));
    }

    @Transactional
    public RoomResponse updateRoom(UUID projectId, UUID roomId, UpdateRoomRequest request, UUID userId) {
        RoomEntity room = getRoomForUpdate(projectId, roomId, userId);

        if (request.getName() != null) {
            String name = normalizeName(request.getName());

            if (roomRepository.existsByProject_IdAndNameAndIdNot(projectId, name, roomId)) {
                throw new ResourceConflictException("Комната с таким названием уже существует в проекте");
            }

            room.setName(name);
        }
        if (request.isIconKeyPresent()) room.setIconKey(request.getIconKey());

        return toResponse(roomRepository.saveAndFlush(room));
    }

    @Transactional
    public void deleteRoom(UUID projectId, UUID roomId, UUID userId) {
        RoomEntity room = getRoomForUpdate(projectId, roomId, userId);

        if (boxRepository.existsByRoom_Id(roomId)) {
            throw new ResourceConflictException("Нельзя удалить комнату с коробками. Сначала перенесите или удалите коробки");
        }

        roomRepository.delete(room);
        roomRepository.flush();
    }

    private RoomEntity getRoomForUpdate(UUID projectId, UUID roomId, UUID userId) {
        projectAccessService.getAccessibleProjectForUpdate(projectId, userId);
        return roomRepository.findForUpdate(roomId, projectId)
                .orElseThrow(() -> new RoomNotFoundException("room not gound"));
    }

    private String normalizeName(String name) {
        String normalized = name == null ? "" : name.strip();

        if (normalized.isBlank()) {
            throw new RequestValidationException("name", "Название комнаты не должно быть пустым");
        }

        return normalized;
    }

    private RoomResponse toResponse(RoomEntity room) {
        return RoomResponse.builder()
                .id(room.getId())
                .projectId(room.getProject().getId())
                .name(room.getName())
                .iconKey(room.getIconKey())
                .createdAt(room.getCreatedAt())
                .updatedAt(room.getUpdatedAt())
                .build();
    }
}
