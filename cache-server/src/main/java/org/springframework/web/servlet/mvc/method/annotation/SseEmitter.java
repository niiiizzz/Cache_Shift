package org.springframework.web.servlet.mvc.method.annotation;

import java.io.IOException;
import java.util.function.Consumer;

public class SseEmitter {

    private final Long timeout;
    private Runnable onCompletion;
    private Runnable onTimeout;
    private Consumer<Throwable> onError;

    public SseEmitter(Long timeout) {
        this.timeout = timeout;
    }

    public void onCompletion(Runnable callback) {
        this.onCompletion = callback;
    }

    public void onTimeout(Runnable callback) {
        this.onTimeout = callback;
    }

    public void onError(Consumer<Throwable> callback) {
        this.onError = callback;
    }

    private Consumer<SseEventBuilder> handler;

    public void setHandler(Consumer<SseEventBuilder> handler) {
        this.handler = handler;
    }

    public void send(SseEventBuilder builder) throws IOException {
        if (handler != null) {
            handler.accept(builder);
        }
    }

    public void complete() {
        if (onCompletion != null) {
            onCompletion.run();
        }
    }

    public void completeWithError(Throwable ex) {
        if (onError != null) {
            onError.accept(ex);
        }
    }

    public static SseEventBuilder event() {
        return new SseEventBuilder();
    }

    public static class SseEventBuilder {
        private String name;
        private Object data;

        public SseEventBuilder name(String name) {
            this.name = name;
            return this;
        }

        public SseEventBuilder data(Object data) {
            this.data = data;
            return this;
        }

        public String getName() { return name; }
        public Object getData() { return data; }
    }
}
