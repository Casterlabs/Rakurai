package co.casterlabs.rakurai.json;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.rakurai.json.annotating.JsonDeserializationMethod;
import co.casterlabs.rakurai.json.annotating.JsonSerializationMethod;
import co.casterlabs.rakurai.json.element.JsonElement;
import co.casterlabs.rakurai.json.element.JsonNull;
import co.casterlabs.rakurai.json.element.JsonObject;
import co.casterlabs.rakurai.json.serialization.JsonParseException;
import co.casterlabs.rakurai.json.serialization.JsonSerializeException;
import co.casterlabs.rakurai.json.validation.JsonValidate;
import co.casterlabs.rakurai.json.validation.JsonValidationException;
import lombok.NonNull;
import lombok.SneakyThrows;
import sun.misc.Unsafe;

@SuppressWarnings("restriction")
public class JsonReflectionUtil {
    private static @Deprecated Unsafe unsafe;

    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = (Unsafe) f.get(null);
        } catch (Exception ignored) {}
    }

    /* -------------------- */
    /* Classes              */
    /* -------------------- */

    @SuppressWarnings("unchecked")
    public static <T> T newInstance(Class<T> clazz) throws InstantiationException, IllegalAccessException {
        if (unsafe == null) {
            return clazz.newInstance();
        }

        // In an ideal world, we don't want to have to worry about a class having a
        // constructor or worry about that constructor throwing. We may need to fallback
        // in the future hence the above code.
        return (T) unsafe.allocateInstance(clazz);
    }

    public static @Nullable Class<?> getCollectionComponent(Class<?> clazz) throws ClassNotFoundException {
        if (clazz.isArray()) {
            return clazz.getComponentType();
        }

        if (Collection.class.isAssignableFrom(clazz)) {
            Type type = clazz.getTypeParameters()[0].getBounds()[0];

            return typeToClass(type, clazz.getClassLoader());
        }

        return null;
    }

    public static @Nullable Class<?>[] getCollectionComponentForField(Field field) throws ClassNotFoundException {
        Class<?> type = field.getType();

        if (type.isArray()) {
            return new Class<?>[] {
                    type.getComponentType()
            };
        }

        if (Collection.class.isAssignableFrom(type)) {
            ParameterizedType genericType = (ParameterizedType) field.getGenericType();
            Type parameter = genericType.getActualTypeArguments()[0];

            return new Class<?>[] {
                    typeToClass(parameter, field.getDeclaringClass().getClassLoader())
            };
        }

        if (Map.class.isAssignableFrom(type)) {
            ParameterizedType pt = (ParameterizedType) field.getGenericType();
            Type[] typeArguments = pt.getActualTypeArguments();

            return new Class<?>[] {
                    typeToClass(typeArguments[0], field.getDeclaringClass().getClassLoader()),
                    typeToClass(typeArguments[1], field.getDeclaringClass().getClassLoader())
            };
        }

        return null;
    }

    @SneakyThrows // We assume that no error will be thrown, ever.
    public static Class<?> typeToClass(@NonNull Type type, @Nullable ClassLoader cl) {
        if (type instanceof Class) {
            return (Class<?>) type; // Sometimes Java actually gives us a Class<?>!
        }

        return Class.forName(type.getTypeName(), false, cl);
    }

    /* -------------------- */
    /* Annotating           */
    /* -------------------- */

    public static @Nullable Collection<JsonValidatorImpl> getJsonValidatorsForClass(Class<?> clazz) {
        List<JsonValidatorImpl> validators = new LinkedList<>();

        for (Method method : getAllDeclaredMethods(clazz)) {
            // Must be void() marked with @JsonValidate
            if (!method.isAnnotationPresent(JsonValidate.class)) continue;
            if (method.getReturnType() != Void.TYPE) continue;
            if (method.getParameterCount() != 0) continue;

            try {
                method.setAccessible(true);
            } catch (Exception ignored) {} // Swallow the error and continue about our day.

            validators.add((@NonNull Object inst) -> {
                try {
                    method.invoke(inst);
                } catch (IllegalAccessException | IllegalArgumentException e) {
                    // Ignore it.
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getCause();
                    if (cause == null) return; // Ignore?

                    if (cause instanceof JsonValidationException) {
                        throw (JsonValidationException) cause; // Rethrow.
                    }

                    throw new JsonValidationException(cause);
                }
            });
        }

        return validators;
    }

    public static @Nullable Collection<JsonDeserializerMethodImpl> getJsonDeserializerMethodsForClass(Class<?> clazz) {
        List<JsonDeserializerMethodImpl> deserializerMethods = new LinkedList<>();

        for (Method method : getAllDeclaredMethods(clazz)) {
            // Must be void(JsonElement) marked with @JsonDeserializationMethod.
            if (!method.isAnnotationPresent(JsonDeserializationMethod.class)) continue;
            if (method.getReturnType() != Void.TYPE) continue;
            if (method.getParameterCount() != 1) continue;
            if (!JsonElement.class.isAssignableFrom(method.getParameterTypes()[0])) continue;

            try {
                method.setAccessible(true);
            } catch (Exception ignored) {} // Swallow the error and continue about our day.

            JsonDeserializationMethod annotation = method.getAnnotation(JsonDeserializationMethod.class);
            deserializerMethods.add(new JsonDeserializerMethodImpl() {
                @Override
                public JsonDeserializationMethod getAnnotation() {
                    return annotation;
                }

                @Override
                public void accept(@NonNull Object inst, @NonNull JsonObject source) throws JsonParseException {
                    try {
                        JsonElement elem = source.get(annotation.value());
                        if (elem == null) return; // Not present.

                        method.invoke(inst, elem);
                    } catch (IllegalAccessException | IllegalArgumentException e) {
                        // Ignore it.
                    } catch (InvocationTargetException e) {
                        Throwable cause = e.getCause();
                        if (cause == null) return; // Ignore?

                        if (cause instanceof JsonParseException) {
                            throw (JsonParseException) cause; // Rethrow.
                        }

                        throw new JsonParseException(cause);
                    }
                }
            });
        }

        // Sort by weight (execution priority).
        deserializerMethods.sort((o1, o2) -> {
            return o1.getAnnotation().weight() < o2.getAnnotation().weight() ? 1 : -1;
        });

        return deserializerMethods;
    }

    public static @Nullable Collection<JsonSerializerMethodImpl> getJsonSerializerMethodsForClass(Class<?> clazz) {
        List<JsonSerializerMethodImpl> serializerMethods = new LinkedList<>();

        for (Method method : getAllDeclaredMethods(clazz)) {
            // Must be JsonElement() marked with @JsonSerializationMethod.
            if (!method.isAnnotationPresent(JsonSerializationMethod.class)) continue;
            if (method.getReturnType() != JsonElement.class) continue;
            if (method.getParameterCount() != 0) continue;

            try {
                method.setAccessible(true);
            } catch (Exception ignored) {} // Swallow the error and continue about our day.

            JsonSerializationMethod annotation = method.getAnnotation(JsonSerializationMethod.class);
            serializerMethods.add(new JsonSerializerMethodImpl() {
                @Override
                public JsonSerializationMethod getAnnotation() {
                    return annotation;
                }

                @Override
                public void generate(@NonNull Object inst, @NonNull JsonObject dest) throws JsonSerializeException {
                    try {
                        JsonElement result = (JsonElement) method.invoke(inst);

                        if (result == null) {
                            result = JsonNull.INSTANCE;
                        }

                        dest.put(annotation.value(), result);
                    } catch (IllegalAccessException | IllegalArgumentException e) {
                        // Ignore it.
                    } catch (InvocationTargetException e) {
                        Throwable cause = e.getCause();
                        if (cause == null) return; // Ignore?

                        if (cause instanceof JsonSerializeException) {
                            throw (JsonSerializeException) cause; // Rethrow.
                        }

                        throw new JsonSerializeException(cause);
                    }
                }
            });
        }

        // Sort by weight (execution priority).
        serializerMethods.sort((o1, o2) -> {
            return o1.getAnnotation().weight() < o2.getAnnotation().weight() ? 1 : -1;
        });

        return serializerMethods;
    }

    /* -------------------- */
    /* Wrappers             */
    /* -------------------- */

    public static interface JsonValidatorImpl {

        public void validate(@NonNull Object inst) throws JsonValidationException;

    }

    public static interface JsonDeserializerMethodImpl {

        public JsonDeserializationMethod getAnnotation();

        public void accept(@NonNull Object inst, @NonNull JsonObject source) throws JsonParseException;

    }

    public static interface JsonSerializerMethodImpl {

        public JsonSerializationMethod getAnnotation();

        public void generate(@NonNull Object inst, @NonNull JsonObject dest) throws JsonSerializeException;

    }

    /* -------------------- */
    /* Method lookup        */
    /* -------------------- */

    private static List<Method> getAllDeclaredMethods(Class<?> clazz) {
        List<Method> methods = new LinkedList<>();

        getAllDeclaredMethods0(methods, clazz);

        return methods;
    }

    private static void getAllDeclaredMethods0(List<Method> methods, Class<?> clazz) {
        Method[] declared = clazz.getDeclaredMethods();

        for (Method m : declared) {
            methods.add(m);
        }

        Class<?> superClass = clazz.getSuperclass();

        if (superClass != null) {
            getAllDeclaredMethods0(methods, superClass);
        }
    }

}
