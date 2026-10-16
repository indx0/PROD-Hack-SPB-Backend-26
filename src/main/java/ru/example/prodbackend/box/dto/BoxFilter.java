package ru.example.prodbackend.box.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.example.prodbackend.box.entity.BoxStatus;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoxFilter {

    private Integer number;

    private UUID roomId;

    private BoxStatus status;

    private Boolean contentsDeferred;

    private int page;

    private int size;
}
