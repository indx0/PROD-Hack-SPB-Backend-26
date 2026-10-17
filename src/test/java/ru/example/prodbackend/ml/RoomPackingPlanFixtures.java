package ru.example.prodbackend.ml;

import ru.example.prodbackend.ml.dto.RoomPackingPlanResponse;
import ru.example.prodbackend.ml.dto.RoomPackingPlanResponse.*;
import java.util.List;

public final class RoomPackingPlanFixtures {
    private RoomPackingPlanFixtures() { }
    public static final String PLAN = """
            {"boxGroups":[
              {"label":"Посуда","count":2,"items":["Кружки","Тарелки"],"packingAdvice":"Обернуть каждый предмет"},
              {"label":"Книги","count":1,"items":["Книги"],"packingAdvice":"Уложить тяжёлое вниз"}],
             "packingOrder":["Упаковать книги","Вещи первого дня упаковать последними"],
             "declutteringSuggestions":[{"item":"Книга","action":"donate","reason":"Если больше не нужна, проверить и отдать"}],
             "assumptions":["Приблизительно для коробок 40×30×30 см и загрузки до 10 кг"]}
            """;
    public static final String NO_PLAN = """
            {"boxGroups":[],"packingOrder":[],"declutteringSuggestions":[],"assumptions":[]}
            """;
    public static RoomPackingPlanResponse response() {
        return new RoomPackingPlanResponse(3, List.of(
                new BoxGroup("Посуда", 2, List.of("Кружки", "Тарелки"), "Обернуть каждый предмет"),
                new BoxGroup("Книги", 1, List.of("Книги"), "Уложить тяжёлое вниз")),
                List.of("Упаковать книги", "Вещи первого дня упаковать последними"),
                List.of(new DeclutteringSuggestion("Книга", Action.DONATE, "Если больше не нужна, проверить и отдать")),
                List.of("Приблизительно для коробок 40×30×30 см и загрузки до 10 кг"));
    }
}
