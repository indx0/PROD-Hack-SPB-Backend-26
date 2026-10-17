package ru.example.prodbackend.box.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoxStatusTest {

    @Test
    void newBoxStartsPacking() {
        assertThat(BoxEntity.builder().build().getStatus()).isEqualTo(BoxStatus.PACKING);
    }

    @ParameterizedTest
    @CsvSource({
            "PACKING, PACKED", "PACKED, LOADED",
            "LOADED, DELIVERED", "DELIVERED, UNPACKED"
    })
    void allowsSequentialForwardStep(BoxStatus current, BoxStatus next) {
        BoxEntity box = BoxEntity.builder().status(current).build();

        box.changeStatus(next);

        assertThat(box.getStatus()).isEqualTo(next);
    }

    @ParameterizedTest
    @EnumSource(BoxStatus.class)
    void allowsRepeatOfCurrentStatus(BoxStatus status) {
        BoxEntity box = BoxEntity.builder().status(status).build();

        box.changeStatus(status);

        assertThat(box.getStatus()).isEqualTo(status);
    }

    @ParameterizedTest
    @CsvSource({
            "PACKING, LOADED", "PACKING, DELIVERED", "PACKING, UNPACKED",
            "PACKED, PACKING", "PACKED, DELIVERED", "PACKED, UNPACKED",
            "LOADED, PACKING", "LOADED, PACKED", "LOADED, UNPACKED",
            "DELIVERED, PACKING", "DELIVERED, PACKED", "DELIVERED, LOADED",
            "UNPACKED, PACKING", "UNPACKED, PACKED", "UNPACKED, LOADED", "UNPACKED, DELIVERED"
    })
    void rejectsSkippedStepsAndBackwardTransitionsWithoutChangingStatus(
            BoxStatus current, BoxStatus next
    ) {
        BoxEntity box = BoxEntity.builder().status(current).build();

        assertThatThrownBy(() -> box.changeStatus(next))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(box.getStatus()).isEqualTo(current);
    }

    @ParameterizedTest
    @EnumSource(BoxStatus.class)
    void rejectsNullWithoutChangingStatus(BoxStatus current) {
        BoxEntity box = BoxEntity.builder().status(current).build();

        assertThatThrownBy(() -> box.changeStatus(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(box.getStatus()).isEqualTo(current);
    }

    @Test
    void correctionAllowsEveryBackwardPairAndRejectsSameForwardAndNull() {
        for (BoxStatus current : BoxStatus.values()) {
            for (BoxStatus target : BoxStatus.values()) {
                BoxEntity box = BoxEntity.builder().status(current).build();
                if (target.ordinal() < current.ordinal()) {
                    box.correctStatus(target);
                    assertThat(box.getStatus()).isEqualTo(target);
                } else {
                    assertThatThrownBy(() -> box.correctStatus(target)).isInstanceOf(IllegalArgumentException.class);
                    assertThat(box.getStatus()).isEqualTo(current);
                }
            }
            BoxEntity box = BoxEntity.builder().status(current).build();
            assertThatThrownBy(() -> box.correctStatus(null)).isInstanceOf(IllegalArgumentException.class);
            assertThat(box.getStatus()).isEqualTo(current);
        }
    }
}
