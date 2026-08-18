package lk.sliit.electronest.admin.controller;

import lk.sliit.electronest.admin.dto.UpdateRoleForm;
import lk.sliit.electronest.admin.dto.UpdateStatusForm;
import lk.sliit.electronest.common.model.User;
import lk.sliit.electronest.common.security.CustomUserDetails;
import lk.sliit.electronest.admin.service.ReportService;
import lk.sliit.electronest.admin.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Every method here requires the ADMIN role - enforced twice: once at the
 * URL level in SecurityConfig ("/admin/**" -> hasRole("ADMIN")) and again
 * per-method with @PreAuthorize as a safety net if the URL rule ever changes.
 */
@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final ReportService reportService;

    // GET /admin/dashboard - the analytics summary cards
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("summary", reportService.getDashboardSummary());
        return "admin/dashboard";
    }

    // GET /admin/users?keyword=... - list all users, optionally filtered by search
    @GetMapping("/users")
    public String listUsers(@RequestParam(required = false) String keyword, Model model) {
        model.addAttribute("users", userService.searchUsers(keyword));
        model.addAttribute("keyword", keyword);
        return "admin/users";
    }

    // GET /admin/users/{id} - view one user + forms to change their role/status
    @GetMapping("/users/{id}")
    public String userDetail(@PathVariable Long id, Model model) {
        User user = userService.getUserById(id);
        model.addAttribute("targetUser", user);
        model.addAttribute("auditLogs", userService.getAuditLogForUser(id));

        if (!model.containsAttribute("updateRoleForm")) {
            UpdateRoleForm roleForm = new UpdateRoleForm();
            roleForm.setNewRole(user.getRole());
            model.addAttribute("updateRoleForm", roleForm);
        }
        if (!model.containsAttribute("updateStatusForm")) {
            UpdateStatusForm statusForm = new UpdateStatusForm();
            statusForm.setNewStatus(user.getStatus());
            model.addAttribute("updateStatusForm", statusForm);
        }

        return "admin/user-detail";
    }

    // POST /admin/users/{id}/role - RBAC role assignment
    @PostMapping("/users/{id}/role")
    public String updateRole(@PathVariable Long id,
                              @Valid @ModelAttribute("updateRoleForm") UpdateRoleForm form,
                              BindingResult bindingResult,
                              @AuthenticationPrincipal CustomUserDetails currentAdmin,
                              RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.updateRoleForm", bindingResult);
            redirectAttributes.addFlashAttribute("updateRoleForm", form);
            return "redirect:/admin/users/" + id;
        }

        userService.updateRole(id, form, currentAdmin.getUser());
        redirectAttributes.addFlashAttribute("successMessage", "Role updated successfully.");
        return "redirect:/admin/users/" + id;
    }

    // POST /admin/users/{id}/status - activate / deactivate / suspend
    @PostMapping("/users/{id}/status")
    public String updateStatus(@PathVariable Long id,
                                @Valid @ModelAttribute("updateStatusForm") UpdateStatusForm form,
                                BindingResult bindingResult,
                                @AuthenticationPrincipal CustomUserDetails currentAdmin,
                                RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.updateStatusForm", bindingResult);
            redirectAttributes.addFlashAttribute("updateStatusForm", form);
            return "redirect:/admin/users/" + id;
        }

        userService.updateStatus(id, form, currentAdmin.getUser());
        redirectAttributes.addFlashAttribute("successMessage", "Account status updated successfully.");
        return "redirect:/admin/users/" + id;
    }

    // POST /admin/users/{id}/delete - deactivate (soft delete, see UserService)
    @PostMapping("/users/{id}/delete")
    public String deactivateUser(@PathVariable Long id,
                                  @AuthenticationPrincipal CustomUserDetails currentAdmin,
                                  RedirectAttributes redirectAttributes) {
        userService.deactivateUser(id, currentAdmin.getUser());
        redirectAttributes.addFlashAttribute("successMessage", "Account deactivated.");
        return "redirect:/admin/users";
    }

    // GET /admin/audit-logs - full role/status change history
    @GetMapping("/audit-logs")
    public String auditLogs(Model model) {
        model.addAttribute("auditLogs", userService.getAllAuditLogs());
        return "admin/audit-logs";
    }
}
