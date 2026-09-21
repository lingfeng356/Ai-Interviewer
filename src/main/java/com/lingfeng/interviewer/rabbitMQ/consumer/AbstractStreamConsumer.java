package com.lingfeng.interviewer.rabbitMQ.consumer;

public abstract class AbstractStreamConsumer<T> {

    protected abstract void handle(T message);
}
