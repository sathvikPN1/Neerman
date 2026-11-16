package com.nirmaan.reimburse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Who people contact for help, e.g. on the "Forgot password?" page. Defaults are Nirmaan's published
 * contacts (nirmaan.iitm.ac.in, contact section and FAQ); override with SUPPORT_* environment variables.
 */
@ConfigurationProperties("app.support")
public record SupportContact(
        @DefaultValue("Nirmaan@smail.iitm.ac.in") String email,
        @DefaultValue("manager.ie@imail.iitm.ac.in") String managerEmail,
        @DefaultValue("") String phone,
        @DefaultValue("Sudha & Shankar Innovation Hub, Indian Institute of Technology Madras, Chennai - 600036, India") String address) {
}
