package ru.example.prodbackend.task.service;

import ru.example.prodbackend.exception.RequestValidationException;
import ru.example.prodbackend.task.entity.MoveTaskEntity;
import ru.example.prodbackend.task.entity.TaskDueType;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** Один расчёт срока для создания, шаблонов, PATCH и переноса проекта. */
public record TaskDeadline(TaskDueType type, Integer offsetDays, LocalDate date) {
    public static TaskDeadline resolve(Instant moveDate, TaskDueType type, Integer offsetDays, LocalDate date, boolean dateProvided) {
        if (type == TaskDueType.RELATIVE) {
            if (dateProvided) throw new RequestValidationException("dueDate", "Для RELATIVE передавайте offsetDays, без dueDate");
            if (offsetDays == null) throw new RequestValidationException("offsetDays", "Для RELATIVE требуется offsetDays");
            return relative(moveDate, offsetDays);
        }
        if (offsetDays != null) throw new RequestValidationException("offsetDays", "offsetDays разрешён только для RELATIVE");
        return new TaskDeadline(TaskDueType.FIXED, null, date);
    }

    public static TaskDeadline relative(Instant moveDate, int offsetDays) {
        try {
            return new TaskDeadline(TaskDueType.RELATIVE, offsetDays, moveDate.atOffset(ZoneOffset.UTC).toLocalDate().plusDays(offsetDays));
        } catch (DateTimeException | ArithmeticException exception) {
            throw new RequestValidationException("offsetDays", "Смещение выходит за допустимый диапазон дат");
        }
    }

    public void applyTo(MoveTaskEntity task) {
        task.setDueType(type);
        task.setOffsetDays(offsetDays);
        task.setDueDate(date);
    }
}
