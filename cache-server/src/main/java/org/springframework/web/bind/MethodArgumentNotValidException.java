package org.springframework.web.bind;

import java.util.Collections;
import java.util.List;

public class MethodArgumentNotValidException extends Exception {

    public BindingResult getBindingResult() {
        return new BindingResult();
    }

    public static class BindingResult {
        public List<FieldError> getFieldErrors() {
            return Collections.emptyList();
        }
    }

    public static class FieldError {
        private final String field;
        private final String defaultMessage;

        public FieldError(String field, String defaultMessage) {
            this.field = field;
            this.defaultMessage = defaultMessage;
        }

        public String getField() { return field; }
        public String getDefaultMessage() { return defaultMessage; }
    }
}
