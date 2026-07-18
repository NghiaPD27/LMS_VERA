package vera.lms.dtos;

import java.util.List;

public class PageDto {
    public record PageResponse<T>(
            List<T> content,
            long totalElements,
            int totalPages,
            int page,
            int size
    ) {}
}
