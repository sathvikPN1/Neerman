package com.nirmaan.reimburse.notification;

/** Outgoing email. Swap implementations via {@code app.mail.mode}. */
public interface EmailSender {
    void send(EmailMessage message);
}
