package ru.example.prodbackend.box.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import ru.example.prodbackend.checklist.entity.FirstDayChecklistItemEntity;
import ru.example.prodbackend.inventory.entity.ItemEntity;
import ru.example.prodbackend.task.entity.MoveTaskEntity;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class BoxEntityTest {
    static Stream<org.junit.jupiter.params.provider.Arguments> transitions() {
        return Arrays.stream(BoxStatus.values()).flatMap(from ->
                Stream.concat(Arrays.stream(BoxStatus.values()), Stream.<BoxStatus>of((BoxStatus) null))
                        .map(to -> org.junit.jupiter.params.provider.Arguments.of(from, to)));
    }

    @ParameterizedTest
    @MethodSource("transitions")
    void allowsOnlySameOrNextStatus(BoxStatus from, BoxStatus to) {
        BoxEntity box = new BoxEntity();
        for (int i = 1; i <= from.ordinal(); i++) {
            box.changeStatus(BoxStatus.values()[i]);
        }
        boolean allowed = to == from || (to != null && to.ordinal() == from.ordinal() + 1);
        if (allowed) {
            box.changeStatus(to);
            assertEquals(to, box.getStatus());
        } else {
            assertThrows(IllegalArgumentException.class, () -> box.changeStatus(to));
            assertEquals(from, box.getStatus());
        }
    }

    @Test
    void newRecordsStartWithUnfinishedWorkAndNoDeferredContents() {
        BoxEntity box = new BoxEntity();
        assertEquals(BoxStatus.PACKING, box.getStatus());
        assertFalse(box.isContentsDeferred());
        assertNull(box.getContentsReminderAt());
        assertFalse(new ItemEntity().isLost());
        assertNull(new MoveTaskEntity().getCompletedAt());
        assertNull(new FirstDayChecklistItemEntity().getCompletedAt());
    }
}
