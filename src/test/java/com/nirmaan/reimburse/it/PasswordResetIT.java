package com.nirmaan.reimburse.it;

import com.nirmaan.reimburse.common.exception.BusinessRuleException;
import com.nirmaan.reimburse.notification.EmailMessage;
import com.nirmaan.reimburse.notification.EmailSender;
import com.nirmaan.reimburse.notification.LoggingEmailSender;
import com.nirmaan.reimburse.user.Permission;
import com.nirmaan.reimburse.user.Role;
import com.nirmaan.reimburse.user.User;
import com.nirmaan.reimburse.user.UserAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Forgot password?": a public page with Nirmaan's contacts, and staff-sent reset links. */
class PasswordResetIT extends IntegrationTest {

    @Autowired UserAdminService userAdmin;
    @Autowired EmailSender emailSender;

    @Test
    void forgotPasswordPageIsPublicAndShowsNirmaanContacts() throws Exception {
        mvc.perform(get("/forgot-password"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Contact <b>Nirmaan staff</b> to reset your password")))
                .andExpect(content().string(containsString("mailto:Nirmaan@smail.iitm.ac.in")))
                .andExpect(content().string(containsString("Sudha &amp; Shankar Innovation Hub")));
        mvc.perform(get("/login")).andExpect(content().string(containsString("href=\"/forgot-password\"")));
    }

    @Test
    void cooSendsAResetLinkThatSetsANewPasswordOnce() throws Exception {
        User member = fx.member(fx.team());
        fx.asRun(fx.coo(), () -> userAdmin.sendPasswordReset(member.getId()));

        String token = lastLinkTo(member.getEmail());
        mvc.perform(post("/invite/{t}", token).with(csrf()).param("password", "Brand-New-Pass-1").param("confirm", "Brand-New-Pass-1"))
                .andExpect(redirectedUrl("/login?activated"));
        mvc.perform(formLogin("/login").user(member.getEmail()).password("Brand-New-Pass-1")).andExpect(redirectedUrl("/"));
        assertThatThrownBy(() -> userAdmin.acceptInvite(token, "Another-Pass-12")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void teamManagersMayResetTeamMembersButNotStaff() {
        User manager = fx.staff(Role.NIRMAAN_STAFF, Permission.TEAM_MANAGE);
        User member = fx.member(fx.team());
        User otherStaff = fx.staff(Role.FINANCE);

        fx.asRun(manager, () -> userAdmin.sendPasswordReset(member.getId()));
        assertThat(lastLinkTo(member.getEmail())).isNotBlank();
        fx.asRun(manager, () -> assertThatThrownBy(() -> userAdmin.sendPasswordReset(otherStaff.getId()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Only the COO"));
    }

    private String lastLinkTo(String email) {
        EmailMessage mail = ((LoggingEmailSender) emailSender).sent().stream().filter(m -> m.to().equals(email))
                .reduce((a, b) -> b).orElseThrow();
        Matcher m = Pattern.compile("/invite/([A-Za-z0-9_-]+)").matcher(mail.body());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }
}
