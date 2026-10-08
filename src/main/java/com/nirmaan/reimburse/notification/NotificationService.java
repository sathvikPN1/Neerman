package com.nirmaan.reimburse.notification;

import com.nirmaan.reimburse.common.exception.NotFoundException;
import com.nirmaan.reimburse.common.security.CurrentUser;
import com.nirmaan.reimburse.config.AppProperties;
import com.nirmaan.reimburse.user.User;
import com.nirmaan.reimburse.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * In-app notifications (persisted in the same transaction as the change) plus email,
 * which is sent only after the transaction commits.
 */
@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final UserRepository users;
    private final EmailSender emailSender;
    private final AppProperties props;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, UserRepository users, EmailSender emailSender,
                               AppProperties props, Clock clock) {
        this.repository = repository;
        this.users = users;
        this.emailSender = emailSender;
        this.props = props;
        this.clock = clock;
    }

    /** Notify users in-app and by email. The acting user is never notified about their own action. */
    @Transactional
    public void notify(Collection<Long> userIds, String message, String link) {
        Long actorId = CurrentUser.get().map(p -> p.id()).orElse(null);
        Instant now = Instant.now(clock);
        List<EmailMessage> emails = new ArrayList<>();
        for (User u : users.findAllById(new LinkedHashSet<>(userIds))) {
            if (!u.isActive() || u.getId().equals(actorId)) {
                continue;
            }
            repository.save(new Notification(u.getId(), message, link, now));
            emails.add(new EmailMessage(u.getEmail(), "[Nirmaan] " + message,
                    "Hello " + u.getName() + ",\n\n" + message + "\n\n" + absolute(link)
                            + "\n\n— Nirmaan Reimbursements"));
        }
        afterCommit(emails);
    }

    /** Send an email that has no in-app counterpart (e.g. invitations). Sent after commit. */
    public void sendEmail(EmailMessage message) {
        afterCommit(List.of(message));
    }

    public String absolute(String link) {
        if (link == null) {
            return props.baseUrl();
        }
        return link.startsWith("http") ? link : props.baseUrl() + link;
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return repository.countByUserIdAndReadFalse(userId);
    }

    @Transactional(readOnly = true)
    public List<NotificationView> recent(Long userId, int limit) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit)).stream()
                .map(n -> new NotificationView(n.getId(), n.getMessage(), n.getLink(), n.isRead(), n.getCreatedAt()))
                .toList();
    }

    @Transactional
    public String open(Long notificationId) {
        Notification n = repository.findById(notificationId)
                .orElseThrow(() -> NotFoundException.of("Notification", notificationId));
        if (!n.getUserId().equals(CurrentUser.require().id())) {
            throw new AccessDeniedException("Not your notification");
        }
        n.markRead();
        return n.getLink() == null ? "/" : n.getLink();
    }

    @Transactional
    public void markAllRead(Long userId) {
        repository.markAllRead(userId);
    }

    private void afterCommit(List<EmailMessage> emails) {
        if (emails.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    emails.forEach(emailSender::send);
                }
            });
        } else {
            emails.forEach(emailSender::send);
        }
    }
}
