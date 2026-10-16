package ru.example.prodbackend.integration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.validation.ConstraintViolationException;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import ru.example.prodbackend.box.entity.*;
import ru.example.prodbackend.checklist.entity.FirstDayChecklistItemEntity;
import ru.example.prodbackend.config.TestcontainersConfig;
import ru.example.prodbackend.event.entity.*;
import ru.example.prodbackend.guide.entity.GuideEntity;
import ru.example.prodbackend.inventory.entity.ItemEntity;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import ru.example.prodbackend.room.entity.RoomEntity;
import ru.example.prodbackend.task.entity.*;
import ru.example.prodbackend.user.entity.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Each test has its own rolled-back transaction and uses real PostgreSQL constraints. */
@DataJpaTest(properties = "spring.jpa.show-sql=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class DomainModelIntegrationTest {
    @Autowired
    private EntityManager em;

    @Test
    void persistsAndReloadsAllEntitiesWithLazyParentsAndStringEnums() {
        MoveProjectEntity project = project();
        project.setOriginAddress("Старый дом");
        project.setDestinationAddress("Новый дом");
        RoomEntity room = room(project, "Кухня");
        BoxEntity box = box(project, room, 1);
        box.changeStatus(BoxStatus.PACKED);
        box.setContentsDeferred(true);
        Instant reminder = Instant.parse("2026-10-20T10:00:00Z");
        box.setContentsReminderAt(reminder);

        ItemEntity item = new ItemEntity();
        item.setBox(box);
        item.setName("Чайник");
        item.setLost(true);
        em.persist(item);

        TaskTemplateEntity template = template("PACK", "Упаковать кухню");
        MoveTaskEntity task = new MoveTaskEntity();
        task.setProject(project);
        task.setTitle(template.getTitle());
        task.setDescription("Осторожно со стеклом");
        task.setStage(template.getStage());
        task.setTemplate(template);
        task.setAssigneeName("Маша");
        task.setDueDate(project.getMoveDate().minusDays(1));
        Instant completed = Instant.parse("2026-10-19T10:00:00Z");
        task.setCompletedAt(completed);
        em.persist(task);

        FirstDayChecklistItemEntity checklist = new FirstDayChecklistItemEntity();
        checklist.setProject(project);
        checklist.setName("Зарядки");
        checklist.setCompletedAt(completed);
        em.persist(checklist);

        GuideEntity guide = guide("FRAGILE");
        ProjectEventEntity event = event(project, box.getId());
        event.setActor(project.getOwner());
        em.flush();
        em.clear();

        BoxEntity loadedBox = em.find(BoxEntity.class, box.getId());
        assertFalse(Hibernate.isInitialized(loadedBox.getRoom()));
        assertFalse(Hibernate.isInitialized(loadedBox.getProject()));
        assertEquals("Кухня", loadedBox.getRoom().getName());
        assertEquals(project.getId(), loadedBox.getRoom().getProject().getId());
        assertEquals(reminder, loadedBox.getContentsReminderAt());
        assertTrue(loadedBox.isContentsDeferred());
        assertEquals(BoxStatus.PACKED, loadedBox.getStatus());
        assertEquals("PACKED", scalar("select status from boxes where id = :id", box.getId()));
        assertEquals("BEFORE_MOVE", scalar("select stage from move_tasks where id = :id", task.getId()));
        assertEquals("BOX_CREATED", scalar("select event_type from project_events where id = :id", event.getId()));
        assertEquals("BOX", scalar("select object_type from project_events where id = :id", event.getId()));
        ItemEntity loadedItem = em.find(ItemEntity.class, item.getId());
        assertTrue(loadedItem.isLost());
        assertEquals(box.getId(), loadedItem.getBox().getId());
        MoveTaskEntity loadedTask = em.find(MoveTaskEntity.class, task.getId());
        assertEquals("Маша", loadedTask.getAssigneeName());
        assertEquals(completed, loadedTask.getCompletedAt());
        assertEquals(project.getMoveDate().minusDays(1), loadedTask.getDueDate());
        assertEquals(completed, em.find(FirstDayChecklistItemEntity.class, checklist.getId()).getCompletedAt());
        assertEquals("Как упаковать хрупкие вещи", em.find(GuideEntity.class, guide.getId()).getTitle());
        assertEquals("Старый дом", loadedBox.getProject().getOriginAddress());
        assertEquals("Новый дом", loadedBox.getProject().getDestinationAddress());
        assertEquals(project.getOwner().getId(), em.find(ProjectEventEntity.class, event.getId()).getActor().getId());
        assertNotNull(loadedBox.getCreatedAt());
        assertNotNull(loadedBox.getUpdatedAt());
        assertNotNull(em.find(ProjectEventEntity.class, event.getId()).getOccurredAt());

        Instant createdAt = loadedTask.getCreatedAt();
        Instant updatedAt = loadedTask.getUpdatedAt();
        em.find(TaskTemplateEntity.class, template.getId()).setTitle("Новый текст шаблона");
        loadedTask.setDescription("Обновлённое описание");
        em.flush();
        em.clear();
        MoveTaskEntity updatedTask = em.find(MoveTaskEntity.class, task.getId());
        assertEquals("Упаковать кухню", updatedTask.getTitle());
        assertEquals(createdAt, updatedTask.getCreatedAt());
        assertTrue(updatedTask.getUpdatedAt().isAfter(updatedAt));
    }

    @Test
    void allowsSameBoxNumberAndRoomNameInDifferentProjects() {
        MoveProjectEntity first = project();
        MoveProjectEntity second = project();
        BoxEntity firstBox = box(first, room(first, "Кухня"), 1);
        BoxEntity secondBox = box(second, room(second, "Кухня"), 1);
        em.flush();
        em.clear();
        assertNotEquals(firstBox.getId(), secondBox.getId());
        assertNotEquals(em.find(BoxEntity.class, firstBox.getId()).getProject().getId(),
                em.find(BoxEntity.class, secondBox.getId()).getProject().getId());
    }

    @Test
    void rejectsDuplicateBoxNumberAcrossRoomsOfSameProject() {
        MoveProjectEntity project = project();
        box(project, room(project, "Кухня"), 1);
        box(project, room(project, "Спальня"), 1);
        assertDatabaseConstraint("uk_boxes_project_number");
    }

    @Test
    void rejectsDuplicateRoomNameInSameProject() {
        MoveProjectEntity project = project();
        room(project, "Кухня");
        room(project, "Кухня");
        assertDatabaseConstraint("uk_rooms_project_name");
    }

    @Test
    void rejectsRoomFromAnotherProject() {
        MoveProjectEntity first = project();
        MoveProjectEntity second = project();
        box(first, room(second, "Кухня"), 1);
        assertDatabaseConstraint("fk_boxes_room_project");
    }

    @Test
    void rejectsDuplicateTemplateCodes() {
        template("PACK", "Первый");
        template("PACK", "Второй");
        assertDatabaseConstraint("uk_task_templates_code");
    }

    @Test
    void rejectsDuplicateGuideCodes() {
        guide("FRAGILE");
        guide("FRAGILE");
        assertDatabaseConstraint("uk_guides_code");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsMissingOrBlankItemName(String name) {
        MoveProjectEntity project = project();
        BoxEntity box = box(project, room(project, "Кухня"), 1);
        em.flush();
        ItemEntity item = new ItemEntity();
        item.setBox(box);
        item.setName(name);
        assertThrows(ConstraintViolationException.class, () -> {
            em.persist(item);
            em.flush();
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonpositiveBoxNumbers(int number) {
        MoveProjectEntity project = project();
        RoomEntity room = room(project, "Кухня");
        em.flush();
        assertThrows(ConstraintViolationException.class, () -> {
            box(project, room, number);
            em.flush();
        });
    }

    @Test
    void rejectsProjectWithoutMoveDate() {
        MoveProjectEntity project = project();
        project.setMoveDate(null);
        assertThrows(ConstraintViolationException.class, em::flush);
    }

    @Test
    void rejectsTaskWithoutStage() {
        MoveTaskEntity task = new MoveTaskEntity();
        task.setProject(project());
        task.setTitle("Заказать грузчиков");
        assertThrows(ConstraintViolationException.class, () -> {
            em.persist(task);
            em.flush();
        });
    }

    @Test
    void databaseRejectsNonpositiveNumberEvenWithoutBeanValidation() {
        MoveProjectEntity project = project();
        BoxEntity box = box(project, room(project, "Кухня"), 1);
        em.flush();
        PersistenceException error = assertThrows(PersistenceException.class, () ->
                em.createNativeQuery("update boxes set number = 0 where id = :id")
                        .setParameter("id", box.getId()).executeUpdate());
        assertTrue(rootMessage(error).contains("ck_boxes_positive_number"));
    }

    @Test
    void databaseRejectsMissingParent() {
        MoveProjectEntity project = project();
        BoxEntity box = box(project, room(project, "Кухня"), 1);
        em.flush();
        assertThrows(PersistenceException.class, () ->
                em.createNativeQuery("update boxes set room_id = null where id = :id")
                        .setParameter("id", box.getId()).executeUpdate());
    }

    @Test
    void databaseRejectsUnknownParent() {
        MoveProjectEntity project = project();
        BoxEntity box = box(project, room(project, "Кухня"), 1);
        em.flush();
        PersistenceException error = assertThrows(PersistenceException.class, () ->
                em.createNativeQuery("update boxes set room_id = :room where id = :id")
                        .setParameter("room", UUID.randomUUID()).setParameter("id", box.getId())
                        .executeUpdate());
        assertTrue(rootMessage(error).contains("fk_boxes_room_project"));
    }

    @Test
    void deletingBoxKeepsItsEventProjectAndOwner() {
        MoveProjectEntity project = project();
        BoxEntity box = box(project, room(project, "Кухня"), 1);
        ProjectEventEntity event = event(project, box.getId());
        em.flush();
        em.remove(box);
        em.flush();
        em.clear();
        assertNull(em.find(BoxEntity.class, box.getId()));
        ProjectEventEntity loadedEvent = em.find(ProjectEventEntity.class, event.getId());
        assertEquals(box.getId(), loadedEvent.getObjectId());
        assertEquals("Коробка создана", loadedEvent.getDescription());
        assertNull(loadedEvent.getActor());
        assertNotNull(em.find(MoveProjectEntity.class, project.getId()));
        assertNotNull(em.find(UserEntity.class, project.getOwner().getId()));
    }

    private MoveProjectEntity project() {
        UserEntity owner = UserEntity.builder().email(UUID.randomUUID() + "@example.org")
                .password("test-password-hash").role(UserRole.USER).build();
        em.persist(owner);
        MoveProjectEntity project = new MoveProjectEntity();
        project.setOwner(owner);
        project.setName("Переезд");
        project.setMoveDate(LocalDate.of(2026, 10, 21));
        em.persist(project);
        return project;
    }

    private RoomEntity room(MoveProjectEntity project, String name) {
        RoomEntity room = new RoomEntity();
        room.setProject(project);
        room.setName(name);
        em.persist(room);
        return room;
    }

    private BoxEntity box(MoveProjectEntity project, RoomEntity room, int number) {
        BoxEntity box = new BoxEntity();
        box.setProject(project);
        box.setRoom(room);
        box.setNumber(number);
        em.persist(box);
        return box;
    }

    private TaskTemplateEntity template(String code, String title) {
        TaskTemplateEntity template = new TaskTemplateEntity();
        template.setCode(code);
        template.setTitle(title);
        template.setStage(TaskStage.BEFORE_MOVE);
        template.setDueOffsetDays(-1);
        em.persist(template);
        return template;
    }

    private GuideEntity guide(String code) {
        GuideEntity guide = new GuideEntity();
        guide.setCode(code);
        guide.setTitle("Как упаковать хрупкие вещи");
        guide.setContent("Оберните каждую вещь защитным материалом.");
        em.persist(guide);
        return guide;
    }

    private ProjectEventEntity event(MoveProjectEntity project, UUID boxId) {
        ProjectEventEntity event = new ProjectEventEntity();
        event.setProject(project);
        event.setEventType(ProjectEventType.BOX_CREATED);
        event.setObjectType(EventObjectType.BOX);
        event.setObjectId(boxId);
        event.setDescription("Коробка создана");
        em.persist(event);
        return event;
    }

    private Object scalar(String sql, UUID id) {
        return em.createNativeQuery(sql).setParameter("id", id).getSingleResult();
    }

    private void assertDatabaseConstraint(String name) {
        PersistenceException error = assertThrows(PersistenceException.class, em::flush);
        assertTrue(rootMessage(error).contains(name), () -> rootMessage(error));
    }

    private String rootMessage(Throwable error) {
        while (error.getCause() != null) {
            error = error.getCause();
        }
        return error.getMessage();
    }
}
