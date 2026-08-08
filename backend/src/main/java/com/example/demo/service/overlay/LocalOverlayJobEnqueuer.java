package com.example.demo.service.overlay;

import com.example.demo.dto.OverlayJobMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnExpression("'${spring.rabbitmq.host:}'.length() == 0 || !'${analysis.worker.mode:local}'.equalsIgnoreCase('ai')")
public class LocalOverlayJobEnqueuer implements OverlayJobEnqueuer {

    @Override
    public void enqueue(OverlayJobMessage message) {
        log.warn(
                "Local overlay enqueue is a no-op; require analysis.worker.mode=ai for baked overlays. overlayJobId={} module={}",
                message.getOverlayJobId(),
                message.getModule()
        );
        throw new IllegalStateException("오버레이 생성은 AI(GPU) 워커 모드에서만 지원됩니다.");
    }
}
