package ru.example.prodbackend.task.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import ru.example.prodbackend.common.dto.PageResponse;
import ru.example.prodbackend.common.dto.PaginationRequest;
import ru.example.prodbackend.exception.TaskTemplateNotFoundException;
import ru.example.prodbackend.task.dto.request.SaveTaskTemplateRequest;
import ru.example.prodbackend.task.dto.response.TaskTemplateResponse;
import ru.example.prodbackend.user.repository.UserRepository;
import ru.example.prodbackend.task.entity.TaskStage;
import ru.example.prodbackend.task.entity.TaskTemplateEntity;
import ru.example.prodbackend.task.repository.TaskTemplateRepository;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskTemplateService {

    private final TaskTemplateRepository taskTemplateRepository;
    private final UserRepository users;

    public PageResponse<TaskTemplateResponse> findAll(UUID userId, TaskStage stage, PaginationRequest pagination) {
        var page = PageRequest.of(pagination.getPage(), pagination.getSize(), Sort.by("title", "id"));
        return PageResponse.from(taskTemplateRepository.findAllByStageFilter(userId, stage, page).map(TaskTemplateService::toResponse));
    }

    public TaskTemplateResponse findById(UUID userId, UUID id) {
        return toResponse(taskTemplateRepository.findAccessible(id, userId).orElseThrow(TaskTemplateService::notFound));
    }

    @Transactional
    public TaskTemplateResponse create(UUID userId, SaveTaskTemplateRequest request) {
        var template = TaskTemplateEntity.builder().owner(users.getReferenceById(userId)).build();
        fill(template, request);
        return toResponse(taskTemplateRepository.saveAndFlush(template));
    }

    @Transactional
    public TaskTemplateResponse replace(UUID userId, UUID id, SaveTaskTemplateRequest request) {
        var template = taskTemplateRepository.findOwnedForUpdate(id, userId).orElseThrow(TaskTemplateService::notFound);
        fill(template, request);
        return toResponse(taskTemplateRepository.saveAndFlush(template));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        var template = taskTemplateRepository.findOwnedForUpdate(id, userId).orElseThrow(TaskTemplateService::notFound);
        template.setDeletedAt(Instant.now());
        taskTemplateRepository.flush();
    }

    private void fill(TaskTemplateEntity template, SaveTaskTemplateRequest request) {
        template.setTitle(request.title().strip());
        var description = request.description() == null ? null : request.description().strip();
        template.setDescription(description == null || description.isBlank() ? null : description);
        template.setStage(request.stage());
        template.setDueDateOffsetDays(request.dueDateOffsetDays());
    }

    public static TaskTemplateResponse toResponse(TaskTemplateEntity template) {
        return TaskTemplateResponse.builder().id(template.getId()).title(template.getTitle()).description(template.getDescription())
                .stage(template.getStage()).dueDateOffsetDays(template.getDueDateOffsetDays()).personal(template.getOwner() != null).build();
    }

    private static TaskTemplateNotFoundException notFound() {
        return new TaskTemplateNotFoundException("Готовая задача не найдена или недоступна");
    }

    /** Локальный справочник для инициализации каталога и явного наполнения демо. */
    @Transactional
    public List<TaskTemplateEntity> getDefaultTemplates() {
        return List.of(
                template(TaskStage.BEFORE_MOVE, "Заказать грузчиков", "Выбрать перевозчика, согласовать дату, время и стоимость.", -14),
                template(TaskStage.BEFORE_MOVE, "Разморозить холодильник", "Отключить холодильник заранее и подготовить его к перевозке.", -1),
                template(TaskStage.BEFORE_MOVE, "Отключить интернет", "Согласовать отключение или перенос договора.", -7),
                template(TaskStage.BEFORE_MOVE, "Собрать коробку первого дня", "Подготовить зарядки, средства гигиены и постельное бельё.", -1),
                template(TaskStage.BEFORE_MOVE, "Разобрать мебель", "Подписать детали и упаковать крепёж отдельно.", -3),
                template(TaskStage.MOVING_DAY, "Погрузить вещи по комнатам", "Проверить коробки и организовать порядок погрузки.", 0),
                template(TaskStage.MOVING_DAY, "Проверить, что ничего не забыто", "Осмотреть комнаты, шкафы и места хранения.", 0),
                template(TaskStage.MOVING_DAY, "Передать ключи", "Проверить квартиру и согласовать передачу ключей.", 0),
                template(TaskStage.AFTER_MOVE, "Распаковать первую комнату", "Подготовить пространство для сна и отдыха.", 1),
                template(TaskStage.AFTER_MOVE, "Подключить интернет", "Проверить подключение и работу оборудования.", 1),
                template(TaskStage.AFTER_MOVE, "Разложить вещи", "Распаковать коробки и проверить наличие вещей.", 3)
        );
    }

    private TaskTemplateEntity template(TaskStage stage, String title, String description, int offset) {
        return taskTemplateRepository.findByStageAndTitleAndOwnerIsNull(stage, title).orElseGet(() -> taskTemplateRepository.save(
                TaskTemplateEntity.builder().stage(stage).title(title).description(description).dueDateOffsetDays(offset).build()));
    }
}
