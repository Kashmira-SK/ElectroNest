package lk.sliit.electronest.admin.dto;

import lk.sliit.electronest.search.model.Review;

public record AdminReviewView(
        Review review,
        String productName,
        String customerName
) {
}
