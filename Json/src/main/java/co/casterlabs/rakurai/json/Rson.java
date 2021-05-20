package co.casterlabs.rakurai.json;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.rakurai.json.annotating.JsonClass;
import co.casterlabs.rakurai.json.annotating.JsonSerializer;
import co.casterlabs.rakurai.json.deserialization.JsonParser;
import co.casterlabs.rakurai.json.element.JsonArray;
import co.casterlabs.rakurai.json.element.JsonBoolean;
import co.casterlabs.rakurai.json.element.JsonElement;
import co.casterlabs.rakurai.json.element.JsonNull;
import co.casterlabs.rakurai.json.element.JsonNumber;
import co.casterlabs.rakurai.json.element.JsonObject;
import co.casterlabs.rakurai.json.element.JsonString;
import co.casterlabs.rakurai.json.serialization.JsonParseException;
import co.casterlabs.rakurai.json.serialization.JsonSerializationContext;
import co.casterlabs.rakurai.json.serialization.JsonSerializeException;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Accessors;

public class Rson {
    public static final Rson DEFAULT = new Rson.Builder()
        .build();

    private static final Map<Class<?>, TypeResolver<?>> resolvers = new HashMap<>();

    private final Builder settings;

    static {
        register((element, type) -> {
            return Byte.valueOf(element.getAsNumber().byteValue());
        }, Byte.class, byte.class);

        register((element, type) -> {
            return Short.valueOf(element.getAsNumber().shortValue());
        }, Short.class, short.class);

        register((element, type) -> {
            return Integer.valueOf(element.getAsNumber().intValue());
        }, Integer.class, int.class);

        register((element, type) -> {
            return Long.valueOf(element.getAsNumber().longValue());
        }, Long.class, long.class);

        register((element, type) -> {
            return Float.valueOf(element.getAsNumber().floatValue());
        }, Float.class, float.class);

        register((element, type) -> {
            return Double.valueOf(element.getAsNumber().doubleValue());
        }, Double.class, double.class);

        register((element, type) -> {
            return element.getAsString();
        }, String.class);
    }

    private static void register(TypeResolver<?> resolver, Class<?>... types) {
        for (Class<?> type : types) {
            resolvers.put(type, resolver);
        }
    }

    private Rson(Builder builder) {
        this.settings = builder;
    }

    public String toJsonString(@Nullable Object o) {
        JsonElement e = this.toJson(o);

        JsonSerializationContext ctx = new JsonSerializationContext()
            .setPrettyPrinting(this.settings.prettyPrintingEnabled)
            .setJson5Enabled(this.settings.json5FeaturesEnabled);

        e.serializeToArray(ctx);

        return ctx.toString();
    }

    public JsonElement toJson(@Nullable Object o) {
        if (o == null) {
            return JsonNull.INSTANCE;
        } else if (o instanceof JsonElement) {
            return (JsonElement) o;
        } else if (o instanceof String) {
            return new JsonString((String) o);
        } else if (o instanceof Enum) {
            return new JsonString((Enum<?>) o);
        } else if (o instanceof Number) {
            return new JsonNumber((Number) o);
        } else if (o instanceof Boolean) {
            return new JsonBoolean((boolean) o);
        } else {
            try {
                Class<?> clazz = o.getClass();

                boolean isCollection = Collection.class.isAssignableFrom(clazz);
                boolean isMap = Map.class.isAssignableFrom(clazz);

                if (isCollection) {
                    JsonArray result = new JsonArray();

                    Collection<?> collection = (Collection<?>) o;

                    for (Object entry : collection) {
                        if (entry == null) {
                            result.addNull();
                        } else {
                            JsonElement serialized = this.toJson(entry);

                            result.add(serialized);
                        }
                    }

                    return result;
                } else if (clazz.isArray()) {
                    JsonArray result = new JsonArray();

                    int len = Array.getLength(o);

                    for (int i = 0; i < len; i++) {
                        Object entry = Array.get(o, i);

                        if (entry == null) {
                            result.addNull();
                        } else {
                            JsonElement serialized = this.toJson(entry);

                            result.add(serialized);
                        }
                    }

                    return result;
                } else if (isMap) {
                    JsonObject result = new JsonObject();

                    Map<?, ?> map = (Map<?, ?>) o;

                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        JsonElement key = this.toJson(entry.getKey());

                        if (key.isJsonString()) {
                            JsonElement value = this.toJson(entry.getValue());

                            result.put(key.getAsString(), value);
                        } else {
                            throw new JsonSerializeException("Map key must be either a String or Enum.");
                        }
                    }

                    return result;
                } else {
                    JsonSerializer<?> serializer;

                    // Create the serializer, or supply a default.
                    {
                        JsonClass classData = clazz.getAnnotation(JsonClass.class);

                        if (classData != null) {
                            Class<? extends JsonSerializer<?>> serializerClass = classData.serializer();
                            Constructor<? extends JsonSerializer<?>> serializerConstructor = serializerClass.getConstructor();

                            serializerConstructor.setAccessible(true);

                            serializer = serializerConstructor.newInstance();
                        } else {
                            serializer = JsonSerializer.DEFAULT;
                        }
                    }

                    return serializer.serialize(o, this);
                }
            } catch (IllegalArgumentException | NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new JsonSerializeException(e);
            }
        }
    }

    public <T> T fromJson(@NonNull String json, @NonNull Class<T> expected) throws JsonParseException {
        JsonElement e = JsonParser.parseString(json, this.settings.json5FeaturesEnabled);

        return this.fromJson(e, expected);
    }

    @SuppressWarnings("unchecked")
    public <T> T fromJson(@NonNull JsonElement e, @NonNull Class<T> expected) throws JsonParseException {
        if (e.isJsonNull()) {
            return null;
        } else if (JsonElement.class.isAssignableFrom(expected)) {
            if (e.getClass() == expected) {
                return (T) e;
            } else {
                throw new JsonParseException(String.format("Expected a %s but got a %s", expected.getSimpleName(), e.getClass().getSimpleName()));
            }
        } else {
            TypeResolver<?> resolver = resolvers.get(expected);

            if (resolver != null) {
                return (T) resolver.resolve(e, expected);
            } else {
                try {
                    if (expected.isArray() != e.isJsonArray()) {
                        throw new JsonParseException(String.format("Expected a %s but got a %s", expected.getSimpleName(), e.getClass().getSimpleName()));
                    } else {
                        if (e.isJsonArray()) {
                            JsonArray array = e.getAsArray();

                            Class<?> type = expected.getComponentType();
                            Object result = Array.newInstance(type, array.size());

                            for (int i = 0; i < array.size(); i++) {
                                Object item = this.fromJson(array.get(i), type);

                                Array.set(result, i, item);
                            }

                            return (T) result;
                        } else {
                            JsonSerializer<?> serializer;

                            // Create the deserializer, or supply a default.
                            {
                                JsonClass classData = expected.getAnnotation(JsonClass.class);

                                if (classData != null) {
                                    Class<? extends JsonSerializer<?>> serializerClass = classData.serializer();
                                    Constructor<? extends JsonSerializer<?>> serializerConstructor = serializerClass.getConstructor();

                                    serializerConstructor.setAccessible(true);

                                    serializer = serializerConstructor.newInstance();
                                } else {
                                    serializer = JsonSerializer.DEFAULT;
                                }
                            }

                            return (T) serializer.deserialize(e, expected, this);
                        }
                    }
                } catch (IllegalArgumentException | NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | InvocationTargetException ex) {
                    throw new JsonSerializeException(ex);
                }
            }
        }
    }

    @Data
    @Accessors(chain = true)
    public static class Builder {
        private boolean json5FeaturesEnabled = false;
        private boolean prettyPrintingEnabled = false;

        public Rson build() {
            return new Rson(this);
        }

    }

}
