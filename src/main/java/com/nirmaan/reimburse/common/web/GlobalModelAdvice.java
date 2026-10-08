package com.nirmaan.reimburse.common.web;

import com.nirmaan.reimburse.common.security.AccessPolicy;
import com.nirmaan.reimburse.common.security.CurrentUser;
import com.nirmaan.reimburse.notification.NotificationService;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Adds the signed-in user and navigation data to every page. */
@ControllerAdvice
public class GlobalModelAdvice {

    private final NotificationService notifications;

    public GlobalModelAdvice(NotificationService notifications) {
        this.notifications = notifications;
    }

    @ModelAttribute
    public void addGlobals(Model model) {
        CurrentUser.get().ifPresent(me -> {
            model.addAttribute("me", me);
            model.addAttribute("staffSide", AccessPolicy.isStaffSide(me));
            model.addAttribute("unreadCount", notifications.unreadCount(me.id()));
        });
    }

}
