package com.lingfeng.interviewer.sse;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


//TODO：前端轮询
@Component
public class SseManager {

    private final Map<String, SseEmitter> emitterMap = new ConcurrentHashMap<>();

    public SseEmitter create(String sessionId) {
        SseEmitter emitter = new SseEmitter(60_000L);
        emitter.onCompletion(() -> emitterMap.remove(sessionId));
        emitter.onTimeout(() -> emitterMap.remove(sessionId));
        emitter.onError((e) -> emitterMap.remove(sessionId));
        emitterMap.put(sessionId, emitter);
        return emitter;
    }

    public void send(String sessionId,String message){
        SseEmitter emitter = emitterMap.get(sessionId);
        if(emitter == null){
            return;
        }

        for(int i = 0;i < 3;i++) {
            try {
                emitter.send(message);
                emitter.complete();
                return;
            } catch (Exception e) {
            }
        }

        emitterMap.remove(sessionId);
    }
}
