package com.possystem.backend.common.cache;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Clean JSON-serializable wrapper for Spring Data {@link Page} results stored in Redis.
 * Avoids PageImpl/Sort Jackson deserialization issues and decouples cached payloads from Hibernate.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CachedPage<T> {
    private List<T> content = new ArrayList<>();
    private int pageNumber;
    private int pageSize;
    private long totalElements;

    public static <T> CachedPage<T> from(Page<T> page) {
        if (page == null) {
            return new CachedPage<>(new ArrayList<>(), 0, 10, 0L);
        }
        return new CachedPage<>(
                new ArrayList<>(page.getContent()),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );
    }

    public Page<T> toPage() {
        int safeSize = pageSize > 0 ? pageSize : Math.max(1, content != null ? content.size() : 10);
        int safePage = Math.max(0, pageNumber);
        List<T> safeContent = content != null ? content : new ArrayList<>();
        return new PageImpl<>(safeContent, PageRequest.of(safePage, safeSize), totalElements);
    }
}
