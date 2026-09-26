package lk.sliit.electronest.search.service;

import jakarta.persistence.criteria.Predicate;
import lk.sliit.electronest.catalog.model.Product;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ProductSpecification {

    public static Specification<Product> filter(String keyword, String category, String brand,
                                                BigDecimal minPrice, BigDecimal maxPrice,
                                                Boolean inStockOnly, Integer minRamGb, Integer minStorageGb) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (keyword != null && !keyword.isBlank()) {
                String likePattern = "%" + keyword.toLowerCase() + "%";
                Predicate nameMatch = cb.like(cb.lower(root.get("name")), likePattern);
                Predicate brandMatch = cb.like(cb.lower(root.get("brand")), likePattern);
                Predicate categoryMatch = cb.like(cb.lower(root.get("category")), likePattern);
                Predicate descMatch = cb.like(cb.lower(root.get("description")), likePattern);
                predicates.add(cb.or(nameMatch, brandMatch, categoryMatch, descMatch));
            }

            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("category")), category.toLowerCase()));
            }

            if (brand != null && !brand.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("brand")), brand.toLowerCase()));
            }

            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), minPrice));
            }

            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice));
            }

            if (Boolean.TRUE.equals(inStockOnly)) {
                predicates.add(cb.equal(root.get("outOfStock"), false));
                predicates.add(cb.greaterThan(root.get("stockQuantity"), 0));
            }

            if (minRamGb != null) predicates.add(cb.greaterThanOrEqualTo(root.get("ramGb"), minRamGb));
            if (minStorageGb != null) predicates.add(cb.greaterThanOrEqualTo(root.get("storageGb"), minStorageGb));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
