package lk.sliit.electronest.common.web;

import lk.sliit.electronest.common.security.CustomUserDetails;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalViewModelAdvice {

    @ModelAttribute
    public void addAuthentication(Authentication authentication, Model model) {
        boolean loggedIn =
                authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);

        model.addAttribute("loggedIn", loggedIn);

        if (loggedIn && authentication.getPrincipal() instanceof CustomUserDetails details) {
            model.addAttribute("currentUser", details.getUser());
        }
    }
}
