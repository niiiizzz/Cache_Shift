package org.springframework.http.converter;

public class HttpMessageNotReadableException extends Exception {
    public HttpMessageNotReadableException(String msg) {
        super(msg);
    }
}
