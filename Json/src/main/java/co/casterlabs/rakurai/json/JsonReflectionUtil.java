package co.casterlabs.rakurai.json;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.rakurai.json.validation.JsonValidate;
import co.casterlabs.rakurai.json.validation.JsonValidationException;
import lombok.NonNull;

public class JsonReflectionUtil {

    public static @Nullable Class<?> getCollectionComponent(Class<?> clazz) throws ClassNotFoundException {
        if (clazz.isArray()) {
            return clazz.getComponentType();
        } else if (Collection.class.isAssignableFrom(clazz)) {
            return Class.forName(clazz.getTypeParameters()[0].getBounds()[0].getTypeName());
        } else {
            return null;
        }
    }

    public static @Nullable Class<?> getCollectionComponentForField(Field field) throws ClassNotFoundException {
        Class<?> type = field.getType();

        if (type.isArray()) {
            return type.getComponentType();
        } else if (Collection.class.isAssignableFrom(type)) {
            String parameter = field.getGenericType().toString();

            parameter = parameter.substring(parameter.indexOf('<') + 1, parameter.length() - 1);

            return Class.forName(parameter);
        } else {
            return null;
        }
    }

    public static @Nullable List<JsonValidator> getJsonValidatorsForClass(Class<?> clazz) {
        List<JsonValidator> validators = new LinkedList<>();

        List<Method> methods = getAllDeclaredMethods(clazz);
        for (Method method : methods) {
            if (method.isAnnotationPresent(JsonValidate.class) &&
                (method.getReturnType() == Void.TYPE) &&
                method.trySetAccessible()) {

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
            }
        }

        return validators;
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

    public static interface JsonValidator {

        public void validate(@NonNull Object inst) throws JsonValidationException;

    }

}
