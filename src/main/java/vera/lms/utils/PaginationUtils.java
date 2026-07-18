package vera.lms.utils;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PaginationUtils {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private PaginationUtils() {
    }

    public static Pageable createPageable(Integer page, Integer size, Sort sort) {
        int safePage = page != null && page >= 0 ? page : DEFAULT_PAGE;
        int requestedSize = size != null && size > 0 ? size : DEFAULT_SIZE;
        int safeSize = Math.min(requestedSize, MAX_SIZE);
        return PageRequest.of(safePage, safeSize, sort != null ? sort : Sort.unsorted());
    }
}
