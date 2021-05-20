package co.casterlabs.rakurai.json;

import java.lang.reflect.Field;
import java.util.Collection;

import org.jetbrains.annotations.Nullable;

public class JsonReflectionUtil {

    public static @Nullable Class<?> getCollectionComponent(Class<?> clazz) throws ClassNotFoundException {
        if (Collection.class.isAssignableFrom(clazz)) {
            return Class.forName(clazz.getTypeParameters()[0].getBounds()[0].getTypeName());
        } else {
            return null;
        }
    }

    public static @Nullable Class<?> getCollectionComponentForField(Field field) throws ClassNotFoundException {
        Class<?> type = field.getType();

        if (Collection.class.isAssignableFrom(type)) {
            String parameter = field.getGenericType().toString();

            parameter = parameter.substring(parameter.indexOf('<') + 1, parameter.length() - 1);

            return Class.forName(parameter);
        } else {
            return null;
        }
    }

}
