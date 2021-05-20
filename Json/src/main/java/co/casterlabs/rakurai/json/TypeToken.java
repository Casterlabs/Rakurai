package co.casterlabs.rakurai.json;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import lombok.SneakyThrows;

public abstract class TypeToken<T> {

    @SneakyThrows
    public Class<?> getTokenClass() {
        String type = getType(this.getClass()).toString();
        // "java.util.List<java.lang.String>"
        String className = type.split("<")[0];
        return Class.forName(className);
    }

    @SneakyThrows
    public String getTokenParameters() {
        String type = getType(this.getClass()).toString();
        // "java.util.List<java.lang.String>"
        String parameters = type.substring(type.indexOf('<') + 1);

        parameters = parameters.substring(0, parameters.length() - 1); // Drop trailing '>'

        return parameters;
    }

    private static Type getType(Class<?> clazz) {
        return getType(clazz, 0);
    }

    private static Type getType(Class<?> clazz, int pos) {
        Type superclass = clazz.getGenericSuperclass();
        Type[] types = ((ParameterizedType) superclass).getActualTypeArguments();

        return types[pos];
    }

}
