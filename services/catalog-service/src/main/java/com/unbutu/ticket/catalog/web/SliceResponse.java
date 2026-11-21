package com.unbutu.ticket.catalog.web;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Slice;

/**
 * Trang kết quả KHÔNG có tổng số (Slice): DB chỉ đọc size+1 dòng để biết còn trang sau, không chạy COUNT(*)
 * – COUNT trên mỗi request là thứ đầu tiên chết khi tải lên hàng chục nghìn rps.
 */
public record SliceResponse<T>(List<T> items, int page, int size, boolean hasNext) {

    public static <E, T> SliceResponse<T> of(Slice<E> slice, Function<E, T> mapper) {
        return new SliceResponse<>(slice.getContent().stream().map(mapper).toList(),
                slice.getNumber(), slice.getSize(), slice.hasNext());
    }
}
