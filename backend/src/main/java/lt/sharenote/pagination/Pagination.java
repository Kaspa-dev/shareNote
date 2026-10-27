package lt.sharenote.pagination;

import lt.sharenote.exception.ApiException;
import org.springframework.http.HttpStatus;

/** Optional, zero-based pagination; omitted parameters preserve the list API. */
public record Pagination(int page, int size) {
    public static Pagination optional(Integer page, Integer size) {
        if (page == null && size == null) return null;
        int requestedPage = page == null ? 0 : page;
        int requestedSize = size == null ? 10 : size;
        if (requestedPage < 0 || requestedSize < 1 || requestedSize > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "page must be non-negative and size must be between 1 and 100.");
        }
        return new Pagination(requestedPage, requestedSize);
    }

    public long offset() { return (long) page * size; }
}
