package jakarta.validation.constraints;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
public @interface DecimalMax {
    String value();
    String message() default "must be less than or equal to {value}";
}
