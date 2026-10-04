package lk.sliit.electronest.admin.controller;

import lk.sliit.electronest.common.model.Role;
import lk.sliit.electronest.admin.dto.RegisterForm;
import lk.sliit.electronest.admin.service.AuthService;
import lk.sliit.electronest.admin.service.RegistrationEmailConflict;
import lk.sliit.electronest.admin.exception.DuplicateResourceException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.dao.DataAccessException;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RegistrationEmailConflict registrationEmailConflict;

    private static final String DUPLICATE_EMAIL_MESSAGE = "An account with this email already exists.";

    // GET /login - Spring Security's formLogin() posts to /login itself;
    // this mapping just renders the login page HTML.
    @GetMapping("/login")
    public String loginPage() {
        return "auth/login";
    }

    // GET /register - show the registration form
    @GetMapping("/register")
    public String registerPage(Model model) {
        if (!model.containsAttribute("registerForm")) {
            model.addAttribute("registerForm", new RegisterForm());
        }
        return "auth/register";
    }

    // POST /register - process the registration form
    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registerForm") RegisterForm form,
                            BindingResult bindingResult,
                            RedirectAttributes redirectAttributes,
                            Model model) {

        if (bindingResult.hasErrors()) {
            form.setPassword(null);
            form.setConfirmPassword(null);
            return "auth/register";
        }

        try {
            form.setRole(Role.CUSTOMER);
            authService.register(form);
        } catch (DuplicateResourceException ex) {
            bindingResult.rejectValue("email", "duplicate", DUPLICATE_EMAIL_MESSAGE);
            form.setPassword(null);
            form.setConfirmPassword(null);
            return "auth/register";
        } catch (DataAccessException ex) {
            // The service transaction has rolled back before inspecting the database metadata.
            if (registrationEmailConflict.matches(ex)) {
                bindingResult.rejectValue("email", "duplicate", DUPLICATE_EMAIL_MESSAGE);
            } else {
                model.addAttribute("errorMessage", "Unable to create your account right now. Please try again.");
            }
            form.setPassword(null);
            form.setConfirmPassword(null);
            return "auth/register";
        } catch (RuntimeException ex) {
            form.setPassword(null);
            form.setConfirmPassword(null);
            model.addAttribute("errorMessage", ex.getMessage());
            return "auth/register";
        }

        redirectAttributes.addFlashAttribute("successMessage",
                "Account created successfully! Please log in.");
        return "redirect:/login";
    }
}
