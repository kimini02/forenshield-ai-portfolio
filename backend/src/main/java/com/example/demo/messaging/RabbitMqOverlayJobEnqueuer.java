package com.example.demo.messaging;

import com.example.demo.config.AnalysisMessagingProperties;
import com.example.demo.dto.OverlayJobMessage;
import com.example.demo.service.overlay.OverlayJobEnqueuer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${spring.rabbitmq.host:}'.length() > 0 && '${analysis.worker.mode:local}'.equalsIgnoreCase('ai')")
public class RabbitMqOverlayJobEnqueuer implements OverlayJobEnqueuer {

    private final RabbitTemplate rabbitTemplate;
    private final AnalysisMessagingProperties messagingProperties;

    @Override
    public void enqueue(OverlayJobMessage message) {
        rabbitTemplate.convertAndSend(
                messagingProperties.getAnalysisExchange(),
                messagingProperties.getOverlayRoutingKey(),
                message,
                msg -> {
                    msg.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return msg;
                }
        );
        log.info(
                "Dispatched overlay job overlayJobId={} evidenceId={} module={} exchange={} routingKey={}",
                message.getOverlayJobId(),
                message.getEvidenceId(),
                message.getModule(),
                messagingProperties.getAnalysisExchange(),
                messagingProperties.getOverlayRoutingKey()
        );
    }
}
