package com.example.demo.messaging;

import com.example.demo.config.AnalysisMessagingProperties;
import com.example.demo.dto.AnalysisJobMessage;
import com.example.demo.service.analysis.AnalysisJobEnqueuer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${spring.rabbitmq.host:}'.length() > 0 && !'${analysis.worker.mode:local}'.equalsIgnoreCase('local')")
public class RabbitMqAnalysisJobEnqueuer implements AnalysisJobEnqueuer {

    private final RabbitTemplate rabbitTemplate;
    private final AnalysisMessagingProperties messagingProperties;

    @Override
    public void enqueue(AnalysisJobMessage message) {
        rabbitTemplate.convertAndSend(
                messagingProperties.getAnalysisExchange(),
                messagingProperties.getVideoAnalysisRoutingKey(),
                message,
                msg -> {
                    msg.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return msg;
                }
        );
        log.info(
                "Dispatched analysis job to GPU queue exchange={} routingKey={} analysisRequestId={} evidenceId={} s3Key={}",
                messagingProperties.getAnalysisExchange(),
                messagingProperties.getVideoAnalysisRoutingKey(),
                message.getAnalysisRequestId(),
                message.getEvidenceId(),
                message.getFilePath()
        );
    }
}
