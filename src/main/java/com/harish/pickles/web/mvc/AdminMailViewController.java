package com.harish.pickles.web.mvc;

import com.harish.pickles.domain.Role;
import com.harish.pickles.domain.User;
import com.harish.pickles.dto.MailDtos.MailFolder;
import com.harish.pickles.dto.MailDtos.MailMessageDetail;
import com.harish.pickles.dto.MailDtos.MailMessageSummary;
import com.harish.pickles.exception.MailAccessException;
import com.harish.pickles.service.MailInboxService;
import com.harish.pickles.service.UserAdminService;
import com.harish.pickles.web.CurrentUser;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The one shared admin mailbox (admin@harifoods.in): inbox, sent mail, and compose. Every admin
 * sees the whole mailbox; a super admin additionally gets a filter that narrows the list to
 * messages involving one registered user's email address - there's no separate per-user mailbox.
 */
@Controller
@RequestMapping("/admin/mail")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMailViewController {

    private static final List<Role> ANY_ROLE = List.of(Role.values());

    private final MailInboxService mailInboxService;
    private final UserAdminService userAdminService;

    public AdminMailViewController(MailInboxService mailInboxService, UserAdminService userAdminService) {
        this.mailInboxService = mailInboxService;
        this.userAdminService = userAdminService;
    }

    @GetMapping
    public String inbox(@RequestParam(defaultValue = "inbox") String folder,
                        @RequestParam(required = false) Long userId,
                        @RequestParam(required = false) String userQuery,
                        Model model) {
        MailFolder mailFolder = parseFolder(folder);
        boolean superAdmin = CurrentUser.require().getRole() == Role.SUPER_ADMIN;

        model.addAttribute("activePage", "mail");
        model.addAttribute("folder", mailFolder.name().toLowerCase(Locale.ROOT));
        model.addAttribute("isSuperAdmin", superAdmin);

        if (superAdmin) {
            model.addAttribute("pickerUsers",
                    userAdminService.list(ANY_ROLE, userQuery, PageRequest.of(0, 50)).getContent());
            model.addAttribute("userQuery", userQuery);
            model.addAttribute("selectedUserId", userId);
        }

        try {
            List<MailMessageSummary> messages = mailInboxService.list(mailFolder);
            // A plain ADMIN never gets the user filter applied, even if userId is forged onto
            // the query string - only a SUPER_ADMIN's selection is honored.
            if (superAdmin && userId != null) {
                User filterUser = userAdminService.getById(userId);
                String email = filterUser.getEmail().toLowerCase(Locale.ROOT);
                messages = messages.stream()
                        .filter(m -> matches(mailFolder, m, email))
                        .toList();
                model.addAttribute("filterUser", filterUser);
            }
            model.addAttribute("messages", messages);
        } catch (MailAccessException ex) {
            model.addAttribute("mailError", ex.getMessage());
            model.addAttribute("messages", List.of());
        }
        return "admin/mail-inbox";
    }

    @GetMapping("/{uid}")
    public String detail(@PathVariable long uid, @RequestParam String folder, Model model) {
        MailFolder mailFolder = parseFolder(folder);
        model.addAttribute("activePage", "mail");
        model.addAttribute("folder", mailFolder.name().toLowerCase(Locale.ROOT));
        try {
            MailMessageDetail message = mailInboxService.fetch(mailFolder, uid);
            model.addAttribute("message", message);
        } catch (MailAccessException ex) {
            model.addAttribute("mailError", ex.getMessage());
        }
        return "admin/mail-detail";
    }

    @GetMapping("/compose")
    public String composeForm(Model model) {
        model.addAttribute("activePage", "mail-compose");
        return "admin/mail-compose";
    }

    @PostMapping("/compose")
    public String compose(@RequestParam String to, @RequestParam String subject,
                          @RequestParam String body, RedirectAttributes flash) {
        try {
            mailInboxService.sendMail(to, subject, body);
            flash.addFlashAttribute("message", "Email sent to " + to);
        } catch (MailAccessException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/mail?folder=sent";
    }

    private MailFolder parseFolder(String folder) {
        return "sent".equalsIgnoreCase(folder) ? MailFolder.SENT : MailFolder.INBOX;
    }

    static boolean matches(MailFolder folder, MailMessageSummary message, String email) {
        String header = folder == MailFolder.SENT ? message.to() : message.from();
        return header != null && header.toLowerCase(Locale.ROOT).contains(email.toLowerCase(Locale.ROOT));
    }
}
