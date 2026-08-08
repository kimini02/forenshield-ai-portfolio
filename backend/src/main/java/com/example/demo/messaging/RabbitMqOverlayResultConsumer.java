package com.example.demo.messaging;

import com.example.demo.config.RabbitMqConfig;
import com.example.demo.dto.OverlayResultMessage;
import com.example.demo.service.overlay.OverlayJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${spring.rabbitmq.host:}'.length() > 0 && '${analysis.worker.mode:local}'.equalsIgnoreCase('ai')")
public class RabbitMqOverlayResultConsumer {

    private final OverlayJobService overlayJobService;

    @RabbitListener(queues = RabbitMqConfig.OVERLAY_RESULT_QUEUE, containerFactory = "rabbitListenerContainerFactory")
    public void consume(OverlayResultMessage message) {
        log.info(
                "Received overlay result overlayJobId={} status={} module={}",
                message.getOverlayJobId(),
                message.getStatus(),
                message.getModule()
        );
        overlayJobService.applyOverlayResult(message);
    }
}
