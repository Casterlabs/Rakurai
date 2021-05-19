package co.casterlabs.rakurai.json.annotating;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(RUNTIME)
@Target(TYPE)
public @interface JsonClass {

    Class<? extends JsonSerializer<?>> serializer() default JsonSerializer.DefaultJsonSerializer.class;

}
