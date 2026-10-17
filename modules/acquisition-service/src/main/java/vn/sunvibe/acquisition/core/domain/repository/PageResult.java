package vn.sunvibe.acquisition.core.domain.repository;

import java.util.List;

/**
 * One page of a search: the items of the page (zero-based {@code page} of {@code size} items) and the number of
 * items over all pages. Lives with the domain repositories because they return it.
 */
public record PageResult<T>(List<T> items, int page, int size, long totalElements) {

    public PageResult {
        items = List.copyOf(items);
    }
}
