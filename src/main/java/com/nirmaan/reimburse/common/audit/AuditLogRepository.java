package com.nirmaan.reimburse.common.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtAscIdAsc(String entityType, Long entityId);

    @Query("""
            select a from AuditLog a
            where (:entityType is null or a.entityType = :entityType)
              and (:action is null or a.action = :action)
            order by a.createdAt desc, a.id desc""")
    Page<AuditLog> search(String entityType, String action, Pageable pageable);

    @Query("select a from AuditLog a where a.entityType = 'PERMISSION' order by a.createdAt desc, a.id desc")
    Page<AuditLog> permissionHistory(Pageable pageable);
}
