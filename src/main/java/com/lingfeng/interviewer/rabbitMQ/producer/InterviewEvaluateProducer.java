package com.lingfeng.interviewer.rabbitMQ.producer;

import com.lingfeng.interviewer.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class InterviewEvaluateProducer extends AbstractStreamProducer<String>{

    public InterviewEvaluateProducer(RabbitTemplate rabbitTemplate){
        super(rabbitTemplate);
    }

    @Override
    protected String getExchange() {
        return RabbitMQConfig.EVALUATE_EXCHANGE;
    }

    @Override
    protected String getRoutingKey() {
        return RabbitMQConfig.EVALUATE_ROUTING_KEY;
    }
}
