package lk.sliit.electronest.common.controller;

import lk.sliit.electronest.common.model.AccountStatus;
import lk.sliit.electronest.common.security.CustomUserDetails;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AccountStatusViewController {

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/account/suspended")
    public String suspended(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model) {
        if (currentUser.getUser().getStatus() != AccountStatus.SUSPENDED) {
            return "redirect:/";
        }

        model.addAttribute("user", currentUser.getUser());
        return "account/suspended";
    }
}
