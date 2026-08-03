package com.example.translator.note;

import org.springframework.data.jpa.domain.Specification;

public class NoteSpecifications {

    private NoteSpecifications() {
    }

    public static Specification<Note> hasRole(Long roleId) {
        return (root, query, cb) -> roleId == null ? null : cb.equal(root.get("role").get("id"), roleId);
    }

    public static Specification<Note> hasContext(Long contextId) {
        return (root, query, cb) -> contextId == null ? null : cb.equal(root.get("context").get("id"), contextId);
    }

    public static Specification<Note> keywordMatches(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) {
                return null;
            }
            String like = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(root.get("originalText")), like),
                    cb.like(cb.lower(root.get("englishResult")), like)
            );
        };
    }
}
