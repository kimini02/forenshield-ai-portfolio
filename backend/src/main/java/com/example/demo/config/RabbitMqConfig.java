package com.example.demo.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
@ConditionalOnExpression("'${spring.rabbitmq.host:}'.length() > 0")
public class RabbitMqConfig {

    public static final String ANALYSIS_QUEUE = "forenshield.analysis.queue";
    public static final String OVERLAY_QUEUE = "forenshield.overlay.queue";
    public static final String RESULT_QUEUE = "backend.ai.result.queue";
    public static final String OVERLAY_RESULT_QUEUE = "backend.ai.overlay.result.queue";
    public static final String ANALYSIS_DLQ = "forenshield.analysis.dlq";

    @Bean
    TopicExchange analysisExchange(AnalysisMessagingProperties properties) {
        return new TopicExchange(properties.getAnalysisExchange(), true, false);
    }

    @Bean
    TopicExchange resultExchange(AnalysisMessagingProperties properties) {
        return new TopicExchange(properties.getResultExchange(), true, false);
    }

    @Bean
    DirectExchange deadLetterExchange(AnalysisMessagingProperties properties) {
        return new DirectExchange(properties.getDeadLetterExchange(), true, false);
    }

    @Bean
    Queue analysisQueue(AnalysisMessagingProperties properties) {
        return QueueBuilder.durable(ANALYSIS_QUEUE)
                .withArgument("x-dead-letter-exchange", properties.getDeadLetterExchange())
                .withArgument("x-dead-letter-routing-key", ANALYSIS_DLQ)
                .build();
    }

    @Bean
    Queue overlayQueue(AnalysisMessagingProperties properties) {
        return QueueBuilder.durable(OVERLAY_QUEUE)
                .withArgument("x-dead-letter-exchange", properties.getDeadLetterExchange())
                .withArgument("x-dead-letter-routing-key", ANALYSIS_DLQ)
                .build();
    }

    @Bean
    Queue analysisResultQueue() {
        return QueueBuilder.durable(RESULT_QUEUE).build();
    }

    @Bean
    Queue overlayResultQueue() {
        return QueueBuilder.durable(OVERLAY_RESULT_QUEUE).build();
    }

    @Bean
    Queue analysisDeadLetterQueue() {
        return QueueBuilder.durable(ANALYSIS_DLQ).build();
    }

    @Bean
    Binding analysisQueueBinding(
            Queue analysisQueue,
            TopicExchange analysisExchange,
            AnalysisMessagingProperties properties
    ) {
        return BindingBuilder.bind(analysisQueue)
                .to(analysisExchange)
                .with(properties.getVideoAnalysisRoutingKey());
    }

    @Bean
    Binding overlayQueueBinding(
            Queue overlayQueue,
            TopicExchange analysisExchange,
            AnalysisMessagingProperties properties
    ) {
        return BindingBuilder.bind(overlayQueue)
                .to(analysisExchange)
                .with(properties.getOverlayRoutingKey());
    }

    @Bean
    Binding analysisResultQueueBinding(
            Queue analysisResultQueue,
            TopicExchange resultExchange,
            AnalysisMessagingProperties properties
    ) {
        return BindingBuilder.bind(analysisResultQueue)
                .to(resultExchange)
                .with(properties.getVideoResultRoutingKey());
    }

    @Bean
    Binding overlayResultQueueBinding(
            Queue overlayResultQueue,
            TopicExchange resultExchange,
            AnalysisMessagingProperties properties
    ) {
        return BindingBuilder.bind(overlayResultQueue)
                .to(resultExchange)
                .with(properties.getOverlayResultRoutingKey());
    }

    @Bean
    Binding analysisDeadLetterBinding(Queue analysisDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(analysisDeadLetterQueue)
                .to(deadLetterExchange)
                .with(ANALYSIS_DLQ);
    }

    @Bean
    MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jacksonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jacksonMessageConverter);
        return rabbitTemplate;
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jacksonMessageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jacksonMessageConverter);
        factory.setPrefetchCount(1);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(1);
        return factory;
    }
}
