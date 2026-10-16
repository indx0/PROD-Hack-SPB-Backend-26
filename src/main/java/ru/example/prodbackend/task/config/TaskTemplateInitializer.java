package ru.example.prodbackend.task.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.annotation.Transactional;
import ru.example.prodbackend.task.service.TaskTemplateService;

@Component
@RequiredArgsConstructor
@Order(0)
public class TaskTemplateInitializer implements ApplicationRunner {

    private final TaskTemplateService taskTemplateService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        taskTemplateService.getDefaultTemplates();
    }
}
