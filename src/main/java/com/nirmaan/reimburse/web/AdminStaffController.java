package com.nirmaan.reimburse.web;

import com.nirmaan.reimburse.common.audit.AuditService;
import com.nirmaan.reimburse.common.time.TimeFormats;
import com.nirmaan.reimburse.user.InviteForm;
import com.nirmaan.reimburse.user.Permission;
import com.nirmaan.reimburse.user.PermissionService;
import com.nirmaan.reimburse.user.Role;
import com.nirmaan.reimburse.user.StaffRow;
import com.nirmaan.reimburse.user.UserAdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Staff & Permissions (COO only). */
@Controller
@PreAuthorize("hasAuthority('STAFF_MANAGE')")
public class AdminStaffController {

    private final UserAdminService userAdmin;
    private final PermissionService permissions;
    private final AuditService audit;

    public AdminStaffController(UserAdminService userAdmin, PermissionService permissions, AuditService audit) {
        this.userAdmin = userAdmin;
        this.permissions = permissions;
        this.audit = audit;
    }

    @GetMapping("/admin/staff")
    public String list(Model model) {
        model.addAttribute("staff", userAdmin.listStaff());
        model.addAttribute("invite", new InviteForm("", "", Role.NIRMAAN_STAFF));
        model.addAttribute("staffRoles", List.of(Role.NIRMAAN_STAFF, Role.FINANCE));
        model.addAttribute("history", audit.permissionHistory(30));
        model.addAttribute("grantable", grantable());
        model.addAttribute("staffDefaults", permissions.roleDefaults(Role.NIRMAAN_STAFF));
        model.addAttribute("financeDefaults", permissions.roleDefaults(Role.FINANCE));
        return "admin/staff";
    }

    @PostMapping("/admin/staff/invite")
    public String invite(@Valid @ModelAttribute("invite") InviteForm form, BindingResult errors,
                         RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            redirect.addFlashAttribute("error", "Enter a name, a valid email and a role.");
            return "redirect:/admin/staff";
        }
        userAdmin.inviteStaff(form);
        redirect.addFlashAttribute("success", "Invitation sent to " + form.email() + ".");
        return "redirect:/admin/staff";
    }

    @GetMapping("/admin/staff/{id}")
    public String edit(@PathVariable Long id, Model model) {
        StaffRow row = userAdmin.getStaff(id);
        model.addAttribute("user", row);
        model.addAttribute("grantable", grantable());
        Map<String, String> expiry = new HashMap<>();
        Set<Permission> held = EnumSet.noneOf(Permission.class);
        row.grants().forEach(g -> {
            held.add(g.permission());
            expiry.put(g.permission().name(), g.expiresAt() == null ? "" : g.expiresAt().minusSeconds(1).atZone(TimeFormats.IST).toLocalDate().toString());
        });
        model.addAttribute("held", held);
        model.addAttribute("expiry", expiry);
        return "admin/staff-edit";
    }

    /**
     * Form fields: {@code perm} (checked permission codes) and {@code expires_<CODE>} (optional yyyy-MM-dd,
     * the grant ends at the end of that day IST).
     */
    @PostMapping("/admin/staff/{id}/permissions")
    public String savePermissions(@PathVariable Long id, @RequestParam(name = "perm", required = false) List<Permission> perms,
                                  HttpServletRequest request, RedirectAttributes redirect) {
        Map<Permission, Instant> desired = new EnumMap<>(Permission.class);
        if (perms != null) {
            for (Permission p : perms) {
                String date = request.getParameter("expires_" + p.name());
                desired.put(p, date == null || date.isBlank() ? null
                        : LocalDate.parse(date).plusDays(1).atStartOfDay(TimeFormats.IST).toInstant());
            }
        }
        permissions.setPermissions(id, desired);
        redirect.addFlashAttribute("success", "Permissions updated. They apply from the user's next click.");
        return "redirect:/admin/staff/" + id;
    }

    @PostMapping("/admin/staff/{id}/deactivate")
    public String deactivate(@PathVariable Long id, RedirectAttributes redirect) {
        userAdmin.deactivate(id);
        redirect.addFlashAttribute("success", "User deactivated and signed out.");
        return "redirect:/admin/staff";
    }

    @PostMapping("/admin/staff/{id}/reactivate")
    public String reactivate(@PathVariable Long id, RedirectAttributes redirect) {
        userAdmin.reactivate(id);
        redirect.addFlashAttribute("success", "User reactivated.");
        return "redirect:/admin/staff";
    }

    @PostMapping("/admin/staff/{id}/resend-invite")
    public String resendInvite(@PathVariable Long id, RedirectAttributes redirect) {
        userAdmin.resendInvite(id);
        redirect.addFlashAttribute("success", "Invitation re-sent.");
        return "redirect:/admin/staff";
    }

    @PostMapping("/admin/staff/transfer-coo")
    public String transferCoo(@RequestParam Long newCooId, @RequestParam String confirm, RedirectAttributes redirect) {
        if (!"TRANSFER".equals(confirm)) {
            redirect.addFlashAttribute("error", "Type TRANSFER to confirm the hand-over.");
            return "redirect:/admin/staff";
        }
        userAdmin.transferCoo(newCooId);
        redirect.addFlashAttribute("success", "The COO role has been transferred. You are now Nirmaan staff.");
        return "redirect:/";
    }

    @PostMapping("/admin/staff/defaults/{role}")
    public String saveDefaults(@PathVariable Role role, @RequestParam(name = "perm", required = false) List<Permission> perms,
                               RedirectAttributes redirect) {
        permissions.setRoleDefaults(role, perms == null ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(perms));
        redirect.addFlashAttribute("success", "Default permissions for " + role.label() + " updated (applies to new users).");
        return "redirect:/admin/staff";
    }

    private static List<Permission> grantable() {
        return Arrays.stream(Permission.values()).filter(Permission::isGrantable).toList();
    }
}
