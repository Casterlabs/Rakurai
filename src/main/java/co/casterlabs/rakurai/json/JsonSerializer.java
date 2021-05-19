package co.casterlabs.rakurai.json;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.rakurai.json.element.JsonElement;
import co.casterlabs.rakurai.json.element.JsonObject;
import co.casterlabs.rakurai.json.serialization.JsonParseException;
import co.casterlabs.rakurai.json.serialization.JsonSerializeException;
import lombok.NonNull;

public interface JsonSerializer<T> {
    public static final JsonSerializer<Object> DEFAULT = new DefaultJsonSerializer();

    default JsonElement serialize(@NonNull Object value, @NonNull Rson rson) {
        return DEFAULT.serialize(value, rson);
    }

    @SuppressWarnings("unchecked")
    default @Nullable T deserialize(@NonNull JsonElement value, @NonNull Class<?> type, @NonNull Rson rson) throws JsonParseException {
        return (T) DEFAULT.deserialize(value, type, rson);
    }

    public static class DefaultJsonSerializer implements JsonSerializer<Object> {

        @Override
        public JsonElement serialize(@NonNull Object value, @NonNull Rson rson) {
            try {
                JsonObject json = new JsonObject();

                for (Field field : value.getClass().getDeclaredFields()) {
                    if (field.isAnnotationPresent(JsonField.class)) {
                        field.setAccessible(true);

                        JsonField fieldAnnotation = field.getDeclaredAnnotation(JsonField.class);
                        String fieldName = fieldAnnotation.value().isEmpty() ? field.getName() : fieldAnnotation.value();

                        JsonElement serialized = rson.toJson(field.get(value));

                        json.put(fieldName, serialized);
                    }
                }

                return json;
            } catch (IllegalArgumentException | IllegalAccessException e) {
                throw new JsonSerializeException(e);
            }
        }

        @Override
        public Object deserialize(@NonNull JsonElement value, @NonNull Class<?> type, @NonNull Rson rson) throws JsonParseException {
            if (value.isJsonObject()) {
                try {
                    JsonObject json = value.getAsObject();
                    Object o;

                    {
                        Constructor<?> serializerConstructor = type.getConstructor();

                        serializerConstructor.setAccessible(true);

                        o = serializerConstructor.newInstance();
                    }

                    for (Field field : type.getDeclaredFields()) {
                        if (field.isAnnotationPresent(JsonField.class)) {
                            field.setAccessible(true);

                            JsonField fieldAnnotation = field.getDeclaredAnnotation(JsonField.class);
                            String fieldName = fieldAnnotation.value().isEmpty() ? field.getName() : fieldAnnotation.value();
                            Class<?> fieldType = field.getType();

                            JsonElement e = json.get(fieldName);

                            // Ignore it if null
                            if (e != null) {
                                if (e.isJsonNull()) {
                                    if (fieldType == boolean.class) {
                                        field.set(o, false);
                                    } else if (fieldType.isPrimitive()) {
                                        field.set(o, 0); // It's a number type.
                                    } else {
                                        field.set(o, null);
                                    }
                                } else {
                                    Object converted = rson.fromJson(e, fieldType);

                                    field.set(o, converted);
                                }
                            }
                        }
                    }

                    return o;
                } catch (IllegalAccessException | InstantiationException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                    throw new JsonParseException(e);
                }
            } else {
                throw new JsonParseException("Expected a JsonObject but got " + value.getClass().getSimpleName());
            }
        }

    }

}
