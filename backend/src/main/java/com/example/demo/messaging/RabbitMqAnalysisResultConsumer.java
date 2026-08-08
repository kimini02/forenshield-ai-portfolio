package com.example.demo.messaging;

import com.example.demo.config.RabbitMqConfig;
import com.example.demo.dto.AnalysisResponseMessage;
import com.example.demo.service.analysis.AnalysisWorkerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${spring.rabbitmq.host:}'.length() > 0 && '${analysis.worker.mode:local}'.equalsIgnoreCase('ai')")
public class RabbitMqAnalysisResultConsumer {

    private final AnalysisWorkerService analysisWorkerService;

    @RabbitListener(queues = RabbitMqConfig.RESULT_QUEUE, containerFactory = "rabbitListenerContainerFactory")
    public void consume(AnalysisResponseMessage message) {
        log.info("Received AI analysis result analysisRequestId={} status={}",
                message.getAnalysisRequestId(), message.getStatus());
        analysisWorkerService.applyAiResult(message);
    }
}
