package lk.sliit.electronest.admin.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    // GET / - simple landing page. Vendor/Customer dashboards belong to other
    // teammates' modules and will link off from here once merged.
    @GetMapping("/")
    public String home() {
        return "home";
    }

    // GET /access-denied - shown when a non-admin tries to open /admin/**
    @GetMapping("/access-denied")
    public String accessDenied() {
        return "error/access-denied";
    }
}
