package com.ms.hrmanagement.controller;

import com.ms.hrmanagement.model.User;
import com.ms.hrmanagement.service.AuthService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/login")
    public String loginPage(
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "logout", required = false) String logout,
            Model model) {

        if (error != null) {
            model.addAttribute("error", "Username or password is incorrect!");
        }

        if (logout != null) {
            model.addAttribute("message", "You have signed out successfully.");
        }

        return "login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {

        model.addAttribute("user", new User());

        return "register";
    }

    @PostMapping("/register")
    public String register(
            @ModelAttribute User user,
            Model model) {

        try {
            authService.registerIntern(user);
            model.addAttribute("message", "Intern account registered successfully!");
            return "login";

        } catch (Exception e) {
            model.addAttribute("error", "Registration failed: " + e.getMessage());
            model.addAttribute("user", user);
            return "register";
        }
    }

    @GetMapping("/auth/check-username")
    @ResponseBody
    public boolean checkUsername(@RequestParam String username) {

        try {
            return authService.usernameExists(username);

        } catch (Exception e) {
            return false;
        }
    }

    @GetMapping("/auth/check-email")
    @ResponseBody
    public boolean checkEmail(@RequestParam String email) {

        try {
            return authService.emailExists(email);

        } catch (Exception e) {
            return false;
        }
    }

    @GetMapping("/auth/user/{id}")
    @ResponseBody
    public User getUserById(@PathVariable String id) {

        try {
            return authService.getUserById(id);

        } catch (Exception e) {
            return null;
        }
    }
}
