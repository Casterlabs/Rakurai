package co.casterlabs.rakurai.json;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

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

public class JsonReflectionUtil {

    public static @Nullable Class<?> getCollectionComponent(Class<?> clazz) throws ClassNotFoundException {
        if (clazz.isArray()) {
            return clazz.getComponentType();
        } else if (Collection.class.isAssignableFrom(clazz)) {
            Type type = clazz.getTypeParameters()[0].getBounds()[0];

            return typeToClass(type, clazz.getClassLoader());
        } else {
            return null;
        }
    }

    public static @Nullable Class<?> getCollectionComponentForField(Field field) throws ClassNotFoundException {
        Class<?> type = field.getType();

        if (type.isArray()) {
            return type.getComponentType();
        } else if (Collection.class.isAssignableFrom(type)) {
            ParameterizedType genericType = (ParameterizedType) field.getGenericType();
            Type parameter = genericType.getActualTypeArguments()[0];

            return typeToClass(parameter, field.getDeclaringClass().getClassLoader());
        } else {
            return null;
        }
    }

    @SneakyThrows
    public static Class<?> typeToClass(@NonNull Type type, @Nullable ClassLoader cl) {
        if (type instanceof Class) {
            return (Class<?>) type;
        } else {
            // Fallback.
            return Class.forName(type.getTypeName(), false, cl);
        }
    }

    public static @Nullable Collection<JsonValidatorImpl> getJsonValidatorsForClass(Class<?> clazz) {
        List<JsonValidatorImpl> validators = new LinkedList<>();

        List<Method> methods = getAllDeclaredMethods(clazz);
        for (Method method : methods) {
            if (method.isAnnotationPresent(JsonValidate.class) &&
                (method.getReturnType() == Void.TYPE) &&
                (method.getParameterCount() == 0)) {
                try {
                    method.setAccessible(true);

                    validators.add((@NonNull Object inst) -> {
                        try {
                            method.invoke(inst);
                        } catch (IllegalAccessException | IllegalArgumentException e) {
                            // Ignore it.
                        } catch (InvocationTargetException e) {
                            Throwable cause = e.getCause();

                            if (cause != null) {
                                if (cause instanceof JsonValidationException) {
                                    throw (JsonValidationException) cause;
                                } else {
                                    throw new JsonValidationException(cause);
                                }
                            }
                        }
                    });
                } catch (Exception ignored) {}
            }
        }

        return validators;
    }

    public static @Nullable Collection<JsonDeserializerMethodImpl> getJsonDeserializerMethodsForClass(Class<?> clazz) {
        List<JsonDeserializerMethodImpl> deserializerMethods = new LinkedList<>();

        List<Method> methods = getAllDeclaredMethods(clazz);
        for (Method method : methods) {
            if (method.isAnnotationPresent(JsonDeserializationMethod.class) &&
                (method.getReturnType() == Void.TYPE) &&
                (method.getParameterCount() == 1) &&
                (method.getParameterTypes()[0] == JsonElement.class)) {
                try {
                    JsonDeserializationMethod annotation = method.getAnnotation(JsonDeserializationMethod.class);

                    method.setAccessible(true);

                    deserializerMethods.add(new JsonDeserializerMethodImpl() {
                        @Override
                        public JsonDeserializationMethod getAnnotation() {
                            return annotation;
                        }

                        @Override
                        public void accept(@NonNull Object inst, @NonNull JsonObject source) throws JsonParseException {
                            try {
                                JsonElement elem = source.get(annotation.value());

                                if (elem != null) {
                                    method.invoke(inst, elem);
                                }
                            } catch (IllegalAccessException | IllegalArgumentException e) {
                                // Ignore it.
                            } catch (InvocationTargetException e) {
                                Throwable cause = e.getCause();

                                if (cause != null) {
                                    if (cause instanceof JsonParseException) {
                                        throw (JsonParseException) cause;
                                    } else {
                                        throw new JsonParseException(cause);
                                    }
                                }
                            }
                        }
                    });
                } catch (Exception ignored) {}
            }
        }

        deserializerMethods.sort((o1, o2) -> {
            return o1.getAnnotation().weight() < o2.getAnnotation().weight() ? 1 : -1;
        });

        return deserializerMethods;
    }

    public static @Nullable Collection<JsonSerializerMethodImpl> getJsonSerializerMethodsForClass(Class<?> clazz) {
        List<JsonSerializerMethodImpl> serializerMethods = new LinkedList<>();

        List<Method> methods = getAllDeclaredMethods(clazz);
        for (Method method : methods) {
            if (method.isAnnotationPresent(JsonSerializationMethod.class) &&
                (method.getReturnType() == JsonElement.class) &&
                (method.getParameterCount() == 0)) {
                try {
                    JsonSerializationMethod annotation = method.getAnnotation(JsonSerializationMethod.class);

                    method.setAccessible(true);

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

                                if (cause != null) {
                                    if (cause instanceof JsonSerializeException) {
                                        throw (JsonSerializeException) cause;
                                    } else {
                                        throw new JsonSerializeException(cause);
                                    }
                                }
                            }
                        }
                    });
                } catch (Exception ignored) {}
            }
        }

        serializerMethods.sort((o1, o2) -> {
            return o1.getAnnotation().weight() < o2.getAnnotation().weight() ? 1 : -1;
        });

        return serializerMethods;
    }

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

}
