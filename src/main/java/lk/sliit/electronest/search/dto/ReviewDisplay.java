package lk.sliit.electronest.search.dto;

import java.time.LocalDateTime;

public record ReviewDisplay(
        Long id,
        int rating,
        String reviewText,
        boolean verified,
        LocalDateTime createdAt,
        String customerName,
        boolean ownedByViewer
) {
}
