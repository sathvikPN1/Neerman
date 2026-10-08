package com.nirmaan.reimburse.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Development sender: writes emails to the log and keeps the most recent ones in memory (useful for tests). */
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);
    private static final int KEEP = 200;

    private final List<EmailMessage> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(EmailMessage message) {
        log.info("EMAIL to={} subject=\"{}\"\n{}", message.to(), message.subject(), message.body());
        sent.add(message);
        while (sent.size() > KEEP) {
            sent.removeFirst();
        }
    }

    public List<EmailMessage> sent() {
        return List.copyOf(sent);
    }
}
