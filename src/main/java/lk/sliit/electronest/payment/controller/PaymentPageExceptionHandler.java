package lk.sliit.electronest.payment.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

import java.util.NoSuchElementException;

@ControllerAdvice(assignableTypes = PaymentViewController.class)
public class PaymentPageExceptionHandler {
    private final lk.sliit.electronest.common.web.GlobalViewModelAdvice viewModel;

    public PaymentPageExceptionHandler(lk.sliit.electronest.common.web.GlobalViewModelAdvice viewModel) {
        this.viewModel = viewModel;
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ModelAndView missing(NoSuchElementException ex) {
        return new ModelAndView("error/404", HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(SecurityException.class)
    public ModelAndView forbidden(SecurityException ex, org.springframework.security.core.Authentication authentication) {
        var model = new org.springframework.ui.ExtendedModelMap();
        viewModel.addAuthentication(authentication, model);
        return new ModelAndView("error/access-denied", model, HttpStatus.FORBIDDEN);
    }
}
