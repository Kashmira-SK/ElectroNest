package lk.sliit.electronest.search.dto;

public record ReviewRequest(
        int rating,
        String reviewText,
        String photoPath
) {
}
