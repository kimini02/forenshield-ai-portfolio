package com.example.demo.messaging;

import com.example.demo.config.RabbitMqConfig;
import com.example.demo.dto.AnalysisJobMessage;
import com.example.demo.service.analysis.AnalysisWorkerService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${spring.rabbitmq.host:}'.length() > 0 && '${analysis.worker.mode:local}'.equalsIgnoreCase('simulated')")
public class RabbitMqAnalysisQueueConsumer {

    private final AnalysisWorkerService analysisWorkerService;

    @RabbitListener(queues = RabbitMqConfig.ANALYSIS_QUEUE, containerFactory = "rabbitListenerContainerFactory")
    public void consume(AnalysisJobMessage message) {
        analysisWorkerService.processJob(message.getAnalysisRequestId());
    }
}
