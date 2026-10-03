package com.harish.pickles.web.mvc;

import com.harish.pickles.dto.AuthDtos.RegisterRequest;
import com.harish.pickles.exception.BusinessException;
import com.harish.pickles.service.AuthService;
import com.harish.pickles.service.PasswordResetService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthViewController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public AuthViewController(AuthService authService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String registerForm() {
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String fullName,
                           @RequestParam String email,
                           @RequestParam(required = false) String phone,
                           @RequestParam String password,
                           Model model, RedirectAttributes flash) {
        if (password == null || password.length() < 8) {
            return back(model, fullName, email, phone, "Password must be at least 8 characters");
        }
        try {
            authService.register(new RegisterRequest(fullName, email, phone == null ? "" : phone, password));
        } catch (BusinessException ex) {
            return back(model, fullName, email, phone, ex.getMessage());
        }
        flash.addFlashAttribute("message", "Account created. Please sign in.");
        return "redirect:/login";
    }

    private String back(Model model, String fullName, String email, String phone, String error) {
        model.addAttribute("fullName", fullName);
        model.addAttribute("email", email);
        model.addAttribute("phone", phone);
        model.addAttribute("error", error);
        return "register";
    }

    // -------------------------------------------------------- forgot password

    @GetMapping("/forgot-password")
    public String forgotPasswordForm() {
        return "forgot-password";
    }

    /** Same answer whether or not the address has an account, so the form cannot be used to probe for members. */
    @PostMapping("/forgot-password")
    public String forgotPassword(@RequestParam String email, RedirectAttributes flash) {
        passwordResetService.requestReset(email);
        flash.addFlashAttribute("message",
                "If an account exists for that email, we have sent a link to reset the password. "
                        + "It works once and expires in an hour.");
        return "redirect:/login";
    }

    @GetMapping("/reset-password")
    public String resetPasswordForm(@RequestParam(required = false) String token, Model model,
                                    HttpServletResponse response) {
        noReferrer(response);
        return resetPage(model, token, null);
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam String token,
                                @RequestParam String password,
                                @RequestParam String confirmPassword,
                                Model model, RedirectAttributes flash, HttpServletResponse response) {
        noReferrer(response);
        if (!password.equals(confirmPassword)) {
            return resetPage(model, token, "The two passwords do not match");
        }
        try {
            passwordResetService.completeReset(token, password);
        } catch (BusinessException ex) {
            return resetPage(model, token, ex.getMessage());
        }
        flash.addFlashAttribute("message", "Your password has been changed. Please sign in.");
        return "redirect:/login";
    }

    /** Shows the new-password form only while the token is genuinely usable; otherwise explains and offers a fresh link. */
    private String resetPage(Model model, String token, String error) {
        boolean valid = passwordResetService.isValid(token);
        model.addAttribute("tokenValid", valid);
        model.addAttribute("token", valid ? token : null);
        model.addAttribute("error", valid ? error
                : "This password reset link is invalid or has expired. Please request a new one.");
        return "reset-password";
    }

    /** The token sits in the URL, so keep it out of the Referer header of anything this page links to. */
    private static void noReferrer(HttpServletResponse response) {
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Cache-Control", "no-store");
    }
}
