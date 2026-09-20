package lk.sliit.electronest.common.web;

import lk.sliit.electronest.cart.controller.CartController;
import lk.sliit.electronest.payment.controller.PaymentController;
import lk.sliit.electronest.payment.controller.ReceiptController;
import lk.sliit.electronest.search.controller.ReviewController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice(assignableTypes = {CartController.class, PaymentController.class,
        ReceiptController.class, ReviewController.class})
public class CommerceApiExceptionHandler {
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> missing(NoSuchElementException ex) {
        return error(HttpStatus.NOT_FOUND, "The requested resource no longer exists.");
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> forbidden(SecurityException ex) {
        return error(HttpStatus.FORBIDDEN, "You do not have permission to access this resource.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalid(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage() == null ? "Invalid request." : ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict(IllegalStateException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage() == null ? "This action is no longer available." : ex.getMessage());
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message));
    }
}
