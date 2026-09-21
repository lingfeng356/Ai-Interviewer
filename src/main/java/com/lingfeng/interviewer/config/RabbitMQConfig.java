package com.lingfeng.interviewer.config;


import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;   // 对的
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EVALUATE_QUEUE = "interview.evaluate.queue";
    public static final String EVALUATE_EXCHANGE = "interview.evaluate.exchange";
    public static final String EVALUATE_ROUTING_KEY = "interview.evaluate";

    //1.消息转换器
    @Bean
    public MessageConverter messageConverter(){
        return new Jackson2JsonMessageConverter();
    }

    //2.交换机
    @Bean
    public DirectExchange evaluateExchange(){
        return new DirectExchange(EVALUATE_EXCHANGE,true,false);
    }

    //3.队列
    @Bean
    public Queue evaluateQueue(){
        return new Queue(EVALUATE_QUEUE,true);
    }

    //4.绑定
    @Bean
    public Binding evaluateBinding(){
        return BindingBuilder.bind(evaluateQueue())
                .to(evaluateExchange())
                .with(EVALUATE_ROUTING_KEY);
    }

    //RabbitTemplate手动绑定消息转换器
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter messageConverter){
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory    );
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }
}
