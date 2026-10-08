package com.nirmaan.reimburse.config;

import com.nirmaan.reimburse.notification.EmailSender;
import com.nirmaan.reimburse.notification.LoggingEmailSender;
import com.nirmaan.reimburse.notification.SmtpEmailSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
public class MailConfig {

    @Bean
    @ConditionalOnProperty(name = "app.mail.mode", havingValue = "smtp")
    EmailSender smtpEmailSender(JavaMailSender mailSender, AppProperties props) {
        return new SmtpEmailSender(mailSender, props.mail().from());
    }

    @Bean
    @ConditionalOnProperty(name = "app.mail.mode", havingValue = "log", matchIfMissing = true)
    EmailSender loggingEmailSender() {
        return new LoggingEmailSender();
    }
}
