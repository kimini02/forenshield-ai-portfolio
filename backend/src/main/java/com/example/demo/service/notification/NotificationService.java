package com.example.demo.service.notification;

import com.example.demo.service.user.UserSettingsService;
import com.example.demo.domain.Notification;
import com.example.demo.domain.enums.BlockchainAnchorType;
import com.example.demo.domain.enums.NotificationType;
import com.example.demo.domain.enums.SecurityAlertCode;
import com.example.demo.dto.notification.NotificationDto;
import com.example.demo.dto.notification.NotificationListResponse;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.util.ApiDateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final String REF_EVIDENCE = "EVIDENCE";
    private static final int SECURITY_ALERT_DEDUP_HOURS = 24;

    private static String securityReferenceType(SecurityAlertCode alertCode) {
        return switch (alertCode) {
            case SIGNATURE_INVALID -> "SEC:SIG_INVALID";
            case CHAIN_INTEGRITY_FAILED -> "SEC:CHAIN_FAIL";
            case BLOCKCHAIN_HASH_MISMATCH -> "SEC:BC_MISMATCH";
            case BLOCKCHAIN_CERT_MISMATCH -> "SEC:BC_CERT_MISMATCH";
        };
    }

    private final NotificationRepository notificationRepository;
    private final UserSettingsService userSettingsService;

    @Transactional(readOnly = true)
    public NotificationListResponse listNotifications(Long userId, int limit) {
        int effectiveLimit = Math.min(Math.max(limit, 1), 50);
        List<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .limit(effectiveLimit)
                .toList();

        int unreadCount = (int) notifications.stream().filter(notification -> !notification.isRead()).count();

        return NotificationListResponse.builder()
                .notifications(notifications.stream().map(this::toDto).toList())
                .unreadCount(unreadCount)
                .build();
    }

    @Transactional
    public NotificationDto markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .filter(item -> item.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "알림을 찾을 수 없습니다."));
        notification.setRead(true);
        return toDto(notificationRepository.save(notification));
    }

    @Transactional
    public int markAllAsRead(Long userId) {
        List<Notification> unread = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .filter(notification -> !notification.isRead())
                .toList();
        unread.forEach(notification -> notification.setRead(true));
        notificationRepository.saveAll(unread);
        return unread.size();
    }

    @Transactional
    public void notifyAnalysisCompleted(Long userId, Long evidenceId, String fileName) {
        if (!userSettingsService.isAnalysisNotificationEnabled(userId)) {
            return;
        }
        create(userId, NotificationType.ANALYSIS_COMPLETED,
                "분석 완료",
                fileName + " 분석이 완료되었습니다.",
                REF_EVIDENCE, evidenceId);
    }

    @Transactional
    public void notifyAnalysisFailed(Long userId, Long evidenceId, String fileName) {
        if (!userSettingsService.isAnalysisNotificationEnabled(userId)) {
            return;
        }
        create(userId, NotificationType.ANALYSIS_FAILED,
                "분석 실패",
                fileName + " 분석이 실패했습니다.",
                REF_EVIDENCE, evidenceId);
    }

    @Transactional
    public void notifyBlockchainAnchored(
            Long userId,
            Long evidenceId,
            BlockchainAnchorType anchorType,
            String transactionHash
    ) {
        if (!userSettingsService.isAnalysisNotificationEnabled(userId)) {
            return;
        }
        String label = anchorType == BlockchainAnchorType.REPORT_HASH
                ? "PDF reportHash"
                : "원본 해시";
        create(userId, NotificationType.BLOCKCHAIN_ANCHOR,
                "블록체인 앵커링 완료",
                label + "가 블록체인에 등록되었습니다. Tx: " + shorten(transactionHash),
                REF_EVIDENCE, evidenceId);
    }

    /**
     * RQ-SEC-153: 보안 경고는 분석 알림 설정과 무관하게 발송 (중복은 24h 억제).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifySecurityAlertIfNeeded(Long userId, Long evidenceId, SecurityAlertCode alertCode) {
        String referenceType = securityReferenceType(alertCode);
        LocalDateTime dedupSince = LocalDateTime.now().minusHours(SECURITY_ALERT_DEDUP_HOURS);
        if (notificationRepository.existsByUserIdAndReferenceTypeAndReferenceIdAndCreatedAtAfter(
                userId, referenceType, evidenceId, dedupSince)) {
            return;
        }

        String title = "보안 경고";
        String message = switch (alertCode) {
            case SIGNATURE_INVALID ->
                    "증거(ID " + evidenceId + ") Manifest X.509 서명 검증에 실패했습니다. 사본 변조가 의심됩니다.";
            case CHAIN_INTEGRITY_FAILED ->
                    "증거(ID " + evidenceId + ") 관리(CoC) 해시 체인 무결성 검증에 실패했습니다.";
            case BLOCKCHAIN_HASH_MISMATCH ->
                    "증거(ID " + evidenceId + ") 블록체인 앵커 해시와 현재 원본 해시가 일치하지 않습니다.";
            case BLOCKCHAIN_CERT_MISMATCH ->
                    "증거(ID " + evidenceId + ") 블록체인 앵커 certVerified와 현재 서명 재검증 결과가 일치하지 않습니다.";
        };

        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(NotificationType.SECURITY_ALERT);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setReferenceType(referenceType);
        notification.setReferenceId(evidenceId);
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }

    private String shorten(String transactionHash) {
        if (transactionHash == null || transactionHash.length() <= 14) {
            return transactionHash;
        }
        return transactionHash.substring(0, 10) + "..." + transactionHash.substring(transactionHash.length() - 4);
    }

    private void create(
            Long userId,
            NotificationType type,
            String title,
            String message,
            String referenceType,
            Long referenceId
    ) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setReferenceType(referenceType);
        notification.setReferenceId(referenceId);
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }

    private NotificationDto toDto(Notification notification) {
        return NotificationDto.builder()
                .notificationId(notification.getNotificationId())
                .type(notification.getType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .referenceType(notification.getReferenceType())
                .referenceId(notification.getReferenceId())
                .read(notification.isRead())
                .createdAt(ApiDateTimeFormatter.formatUtc(notification.getCreatedAt()))
                .build();
    }
}
