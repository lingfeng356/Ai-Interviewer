package com.lingfeng.interviewer.rabbitMQ.producer;

import org.springframework.amqp.rabbit.core.RabbitTemplate;

public abstract class AbstractStreamProducer<T> {

    protected final RabbitTemplate rabbitTemplate;

    public AbstractStreamProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    protected abstract String getExchange();
    protected abstract String getRoutingKey();

    /*
    * 发送消息
    * */
    public void send(T message){
        rabbitTemplate.convertAndSend(
                getExchange(),
                getRoutingKey(),
                message
        );
    }
}
