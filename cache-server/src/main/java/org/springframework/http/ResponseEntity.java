package org.springframework.http;

public class ResponseEntity<T> {

    private final HttpStatus status;
    private final T body;

    public ResponseEntity(HttpStatus status, T body) {
        this.status = status;
        this.body = body;
    }

    public static <T> ResponseEntity<T> ok(T body) {
        return new ResponseEntity<>(HttpStatus.OK, body);
    }

    public static BodyBuilder ok() {
        return new DefaultBodyBuilder(HttpStatus.OK);
    }

    public static BodyBuilder status(HttpStatus status) {
        return new DefaultBodyBuilder(status);
    }

    public static BodyBuilder notFound() {
        return new DefaultBodyBuilder(HttpStatus.NOT_FOUND);
    }

    public HttpStatus getStatusCode() {
        return status;
    }

    public T getBody() {
        return body;
    }

    public interface BodyBuilder {
        <T> ResponseEntity<T> body(T body);
        <T> ResponseEntity<T> build();
    }

    private static class DefaultBodyBuilder implements BodyBuilder {
        private final HttpStatus status;

        DefaultBodyBuilder(HttpStatus status) {
            this.status = status;
        }

        @Override
        public <T> ResponseEntity<T> body(T body) {
            return new ResponseEntity<>(status, body);
        }

        @Override
        public <T> ResponseEntity<T> build() {
            return new ResponseEntity<>(status, null);
        }
    }
}
