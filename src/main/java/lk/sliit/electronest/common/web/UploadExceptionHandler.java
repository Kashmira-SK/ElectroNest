package lk.sliit.electronest.common.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;

@ControllerAdvice
public class UploadExceptionHandler {
    // Render directly so error dispatch does not parse the oversized body again.
    @ExceptionHandler(value = MaxUploadSizeExceededException.class, produces = "text/html")
    public ModelAndView page(MaxUploadSizeExceededException ex) {
        return new ModelAndView("error/413", HttpStatus.CONTENT_TOO_LARGE);
    }

    @ExceptionHandler(value = MaxUploadSizeExceededException.class, produces = "application/json")
    public ResponseEntity<Map<String, String>> json(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(Map.of("message", "Upload exceeds the permitted size."));
    }
}
