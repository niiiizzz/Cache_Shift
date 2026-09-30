package jakarta.validation.constraints;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
public @interface DecimalMin {
    String value();
    String message() default "must be greater than or equal to {value}";
}
