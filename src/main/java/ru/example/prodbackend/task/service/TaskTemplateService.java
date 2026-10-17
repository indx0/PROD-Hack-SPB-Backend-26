package ru.example.prodbackend.task.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.example.prodbackend.task.entity.TaskStage;
import ru.example.prodbackend.task.entity.TaskTemplateEntity;
import ru.example.prodbackend.task.repository.TaskTemplateRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskTemplateService {

    private final TaskTemplateRepository taskTemplateRepository;

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
        return taskTemplateRepository.findByStageAndTitle(stage, title).orElseGet(() -> taskTemplateRepository.save(
                TaskTemplateEntity.builder().stage(stage).title(title).description(description).dueDateOffsetDays(offset).build()));
    }
}
