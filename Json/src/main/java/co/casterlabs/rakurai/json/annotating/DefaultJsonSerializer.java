package co.casterlabs.rakurai.json.annotating;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import co.casterlabs.rakurai.json.JsonReflectionUtil;
import co.casterlabs.rakurai.json.Rson;
import co.casterlabs.rakurai.json.element.JsonElement;
import co.casterlabs.rakurai.json.element.JsonObject;
import co.casterlabs.rakurai.json.serialization.JsonParseException;
import co.casterlabs.rakurai.json.serialization.JsonSerializeException;
import lombok.NonNull;

public class DefaultJsonSerializer implements JsonSerializer<Object> {

    private static Map<String, Field> getFields(Class<?> type) {
        Map<String, Field> fields = new HashMap<>();
        List<Class<?>> toScan = new LinkedList<>();

        Class<?> currentClass = type;
        while (currentClass != null) {
            boolean exposeSuper = false;

            if (currentClass.isAnnotationPresent(JsonClass.class)) {
                JsonClass classAnnotation = currentClass.getAnnotation(JsonClass.class);

                exposeSuper = classAnnotation.exposeSuper();
            }

            if (exposeSuper) {
                toScan.addAll(Arrays.asList(currentClass.getInterfaces()));

                if (currentClass.getSuperclass() != null) {
                    toScan.add(currentClass.getSuperclass());
                }
            }

            toScan.add(currentClass);

            currentClass = currentClass.getSuperclass();
        }

        for (Class<?> clazz : toScan) {
            boolean exposeAll = false;

            if (clazz.isAnnotationPresent(JsonClass.class)) {
                JsonClass classAnnotation = clazz.getAnnotation(JsonClass.class);

                exposeAll = classAnnotation.exposeAll();
            }

            for (Field field : clazz.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    if (exposeAll || field.isAnnotationPresent(JsonField.class)) {
                        String fieldName = field.getName();

                        if (field.isAnnotationPresent(JsonField.class)) {
                            JsonField fieldAnnotation = field.getAnnotation(JsonField.class);

                            if (!fieldAnnotation.value().isEmpty()) {
                                fieldName = fieldAnnotation.value();
                            }
                        }

                        fields.put(fieldName, field);
                    }
                }
            }
        }

        return fields;
    }

    @Override
    public JsonElement serialize(@NonNull Object value, @NonNull Rson rson) {
        try {
            JsonObject json = new JsonObject();
            Class<?> type = value.getClass();

            Map<String, Field> fields = getFields(type);

            for (Entry<String, Field> entry : fields.entrySet()) {
                String fieldName = entry.getKey();
                Field field = entry.getValue();

                field.setAccessible(true);

                JsonElement serialized = rson.toJson(field.get(value));

                json.put(fieldName, serialized);
            }

            return json;
        } catch (IllegalArgumentException | IllegalAccessException e) {
            throw new JsonSerializeException(e);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public Object deserialize(@NonNull JsonElement value, @NonNull Class<?> type, @NonNull Rson rson) throws JsonParseException {
        if (value.isJsonObject()) {
            try {
                JsonObject json = value.getAsObject();
                Object o;

                {
                    Constructor<?> typeConstructor = type.getConstructor();

                    typeConstructor.setAccessible(true);

                    o = typeConstructor.newInstance();
                }

                Map<String, Field> fields = getFields(type);

                for (Entry<String, Field> entry : fields.entrySet()) {
                    String fieldName = entry.getKey();
                    Field field = entry.getValue();

                    field.setAccessible(true);

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
                            Class<?> fieldComponent = JsonReflectionUtil.getCollectionComponentForField(field);

                            Object converted = rson.fromJson(e, fieldType, fieldComponent);

                            field.set(o, converted);
                        }
                    }
                }

                return o;
            } catch (IllegalAccessException | InstantiationException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException | ClassNotFoundException e) {
                throw new JsonParseException(e);
            }
        } else {
            throw new JsonParseException(String.format("Expected a JsonObject but got %s for %s", value.getClass().getSimpleName(), type));
        }
    }

}
