package co.casterlabs.rakurai.json;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(RUNTIME)
@Target(TYPE)
public @interface JsonClass {

    Class<? extends JsonSerializer<?>> serializer() default JsonSerializer.DefaultJsonSerializer.class;

}
