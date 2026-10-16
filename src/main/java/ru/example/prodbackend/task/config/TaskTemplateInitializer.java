package ru.example.prodbackend.task.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.example.prodbackend.task.entity.TaskStage;
import ru.example.prodbackend.task.entity.TaskTemplateEntity;
import ru.example.prodbackend.task.repository.TaskTemplateRepository;

@Component
@RequiredArgsConstructor
public class TaskTemplateInitializer implements ApplicationRunner {

    private final TaskTemplateRepository taskTemplateRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        add(TaskStage.BEFORE_MOVE, "Заказать грузчиков", "Выбрать перевозчика, согласовать дату, время и стоимость.");
        add(TaskStage.BEFORE_MOVE, "Разморозить холодильник", "Отключить холодильник заранее и подготовить его к перевозке.");
        add(TaskStage.BEFORE_MOVE, "Отключить интернет", "Согласовать отключение или перенос договора.");
        add(TaskStage.BEFORE_MOVE, "Собрать коробку первого дня", "Подготовить зарядки, средства гигиены и постельное бельё.");
        add(TaskStage.BEFORE_MOVE, "Разобрать мебель", "Подписать детали и упаковать крепёж отдельно.");

        add(TaskStage.MOVING_DAY, "Погрузить вещи по комнатам", "Проверить коробки и организовать порядок погрузки.");
        add(TaskStage.MOVING_DAY, "Проверить, что ничего не забыто", "Осмотреть комнаты, шкафы и места хранения.");
        add(TaskStage.MOVING_DAY, "Передать ключи", "Проверить квартиру и согласовать передачу ключей.");

        add(TaskStage.AFTER_MOVE, "Распаковать первую комнату", "Подготовить пространство для сна и отдыха.");
        add(TaskStage.AFTER_MOVE, "Подключить интернет", "Проверить подключение и работу оборудования.");
        add(TaskStage.AFTER_MOVE, "Разложить вещи", "Распаковать коробки и проверить наличие вещей.");
    }

    private void add(TaskStage stage, String title, String description) {
        if (!taskTemplateRepository.existsByStageAndTitle(stage, title)) {
            taskTemplateRepository.save(TaskTemplateEntity.builder().title(title).description(description).stage(stage).build());
        }
    }
}
