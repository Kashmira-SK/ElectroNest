package lk.sliit.electronest.admin.controller;

import lk.sliit.electronest.common.model.Role;
import lk.sliit.electronest.admin.dto.RegisterForm;
import lk.sliit.electronest.admin.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
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
