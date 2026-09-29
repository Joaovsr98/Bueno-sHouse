package com.restaurante.sistema.config;

import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * Paginacao opcional (RNF08) para listagens pequenas: sem `page` devolve a lista completa
 * (compativel com telas atuais); com `page` (0-based) devolve a fatia e o total em X-Total-Count.
 */
public final class Paging {

    private static final int MAX_SIZE = 100;

    private Paging() {}

    public static <T> ResponseEntity<List<T>> respond(List<T> all, Integer page, int size) {
        if (page == null) {
            return ResponseEntity.ok(all);
        }
        int s = Math.max(1, Math.min(size, MAX_SIZE));
        int from = (int) Math.min((long) Math.max(page, 0) * s, all.size());
        int to = Math.min(from + s, all.size());
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(all.size()))
                .body(all.subList(from, to));
    }
}
