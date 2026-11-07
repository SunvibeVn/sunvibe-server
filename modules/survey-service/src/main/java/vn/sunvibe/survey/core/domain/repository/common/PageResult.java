package vn.sunvibe.survey.core.domain.repository.common;

import java.util.List;

/** One page of results: the items, the zero-based page, its size and the number of matches over all pages. */
public record PageResult<T>(List<T> items, int page, int size, long totalElements) {

    public PageResult {
        items = List.copyOf(items);
    }
}
