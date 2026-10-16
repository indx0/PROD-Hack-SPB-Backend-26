package ru.example.prodbackend.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.springframework.data.domain.Page;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    @Schema(description = "Элементы текущей страницы")
    private List<T> content;

    @Schema(description = "Номер страницы, начиная с 0")
    private int page;

    @Schema(description = "Размер страницы")
    private int size;

    @Schema(description = "Общее количество элементов с учетом фильтров")
    private long totalElements;

    @Schema(description = "Общее количество страниц с учетом фильтров")
    private int totalPages;

    public static <T> PageResponse<T> from(Page<T> source) {
        return PageResponse.<T>builder()
                .content(source.getContent())
                .page(source.getNumber())
                .size(source.getSize())
                .totalElements(source.getTotalElements())
                .totalPages(source.getTotalPages())
                .build();
    }
}
