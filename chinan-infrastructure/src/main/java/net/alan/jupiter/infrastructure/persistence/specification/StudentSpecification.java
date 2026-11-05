package net.alan.jupiter.infrastructure.persistence.specification;

import jakarta.persistence.criteria.Predicate;
import net.alan.jupiter.domain.model.Student;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class StudentSpecification {

    private StudentSpecification() {
    }

    public static Specification<Student> searchBy(String keyword, String buildingId, String className) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (buildingId != null && !buildingId.isBlank()) {
                predicates.add(cb.equal(root.get("building").get("id"), buildingId));
            }
            if (className != null && !className.isBlank()) {
                predicates.add(cb.like(root.get("className"), "%" + className + "%"));
            }
            if (keyword != null && !keyword.isBlank()) {
                Predicate nameLike = cb.like(root.get("name"), "%" + keyword + "%");
                Predicate noLike = cb.like(root.get("studentNo"), "%" + keyword + "%");
                predicates.add(cb.or(nameLike, noLike));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}