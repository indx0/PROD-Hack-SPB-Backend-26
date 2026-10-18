package ru.example.prodbackend.unit.ml.service;

import ru.example.prodbackend.ml.service.UnpackingInventoryService;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import ru.example.prodbackend.box.entity.BoxEntity;
import ru.example.prodbackend.box.repository.BoxRepository;
import ru.example.prodbackend.exception.BoxNotFoundException;
import ru.example.prodbackend.item.entity.ItemEntity;
import ru.example.prodbackend.item.repository.ItemRepository;
import ru.example.prodbackend.ml.exception.MlException;
import ru.example.prodbackend.move.service.ProjectAccessService;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UnpackingInventoryServiceTest {
    private final ProjectAccessService access = mock(ProjectAccessService.class);
    private final BoxRepository boxes = mock(BoxRepository.class);
    private final ItemRepository items = mock(ItemRepository.class);
    private final UUID user = UUID.randomUUID(), project = UUID.randomUUID(), box = UUID.randomUUID();
    private final UnpackingInventoryService service = new UnpackingInventoryService(access, boxes, items);

    @Test
    void snapshotIsImmutableOrderedAndLimitedWithoutTruncating() {
        when(boxes.findByIdAndProject_Id(box, project)).thenReturn(Optional.of(new BoxEntity()));
        var entity = ItemEntity.builder().id(UUID.randomUUID()).name("Кружка").build();
        when(items.findAllByBox_Id(eq(box), any(Pageable.class))).thenAnswer(call -> {
            Pageable page = call.getArgument(1);
            assertThat(page.getPageSize()).isEqualTo(101);
            assertThat(page.getSort().stream().map(order -> order.getProperty()).toList()).containsExactly("createdAt", "id");
            return new PageImpl<>(List.of(entity), page, 1);
        });
        var snapshot = service.snapshot(user, project, box);
        entity.setName("Changed");
        assertThat(snapshot.get(0).getName()).isEqualTo("Кружка");
        assertThatThrownBy(snapshot::clear).isInstanceOf(UnsupportedOperationException.class);
        var order = inOrder(access, boxes, items);
        order.verify(access).getAccessibleProject(project, user);
        order.verify(boxes).findByIdAndProject_Id(box, project);
        order.verify(items).findAllByBox_Id(eq(box), any(Pageable.class));
    }

    @Test
    void missingBoxStopsBeforeInventoryRead() {
        when(boxes.findByIdAndProject_Id(box, project)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.snapshot(user, project, box)).isInstanceOf(BoxNotFoundException.class);
        verifyNoInteractions(items);
    }

    @Test
    void enforcesInventoryBoundaries() {
        when(boxes.findByIdAndProject_Id(box, project)).thenReturn(Optional.of(new BoxEntity()));
        when(items.findAllByBox_Id(eq(box), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        assertCode("EMPTY_INVENTORY");
        var entities = java.util.stream.IntStream.range(0, 100)
                .mapToObj(i -> ItemEntity.builder().id(UUID.randomUUID()).name("Кружка").build()).toList();
        when(items.findAllByBox_Id(eq(box), any(Pageable.class))).thenReturn(new PageImpl<>(entities));
        assertThat(service.snapshot(user, project, box)).hasSize(100);
        when(items.findAllByBox_Id(eq(box), any(Pageable.class))).thenReturn(
                new PageImpl<>(entities, org.springframework.data.domain.PageRequest.of(0, 101), 101));
        assertCode("INVENTORY_TOO_LARGE");
    }

    private void assertCode(String code) {
        assertThatThrownBy(() -> service.snapshot(user, project, box)).isInstanceOfSatisfying(MlException.class,
                error -> assertThat(error.getCode()).isEqualTo(code));
    }
}
