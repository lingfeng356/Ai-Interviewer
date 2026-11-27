package com.lingfeng.interviewer.rabbitMQ.producer;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

public abstract class AbstractStreamProducer<T> {

    protected final RabbitTemplate rabbitTemplate;

    public AbstractStreamProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    protected abstract String getExchange();
    protected abstract String getRoutingKey();

    /*
    * 发送消息。
    *
    * convertAndSend 只代表“交给了 channel”，不代表 Broker 已经落盘。
    * 这里再同步等一次确认：没确认就抛异常，让调用方（结束面试）把状态改回去让用户重试，
    * 而不是悄悄丢一条消息、让报告永远停在“生成中”。
    *
    * 依赖 application.yml 里的 publisher-confirm-type: correlated。
    * */
    public void send(T message){
        rabbitTemplate.convertAndSend(
                getExchange(),
                getRoutingKey(),
                message
        );
        try {
            rabbitTemplate.waitForConfirmsOrDie(5_000);
        } catch (Exception e) {
            throw new AmqpException("消息未被 Broker 确认", e);
        }
    }
}
