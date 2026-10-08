package com.nirmaan.reimburse.web;

import com.nirmaan.reimburse.common.security.CurrentUser;
import com.nirmaan.reimburse.notification.NotificationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping("/notifications")
    public String list(Model model) {
        model.addAttribute("notifications", notifications.recent(CurrentUser.require().id(), 100));
        return "notifications/list";
    }

    @GetMapping("/notifications/{id}/open")
    public String open(@PathVariable Long id) {
        String link = notifications.open(id);
        return "redirect:" + (link.startsWith("/") && !link.startsWith("//") ? link : "/");
    }

    @PostMapping("/notifications/read-all")
    public String readAll() {
        notifications.markAllRead(CurrentUser.require().id());
        return "redirect:/notifications";
    }

    /** HTMX: polled badge in the header. */
    @GetMapping("/notifications/badge")
    public String badge(Model model) {
        model.addAttribute("unreadCount", notifications.unreadCount(CurrentUser.require().id()));
        return "fragments/layout :: badge";
    }
}
