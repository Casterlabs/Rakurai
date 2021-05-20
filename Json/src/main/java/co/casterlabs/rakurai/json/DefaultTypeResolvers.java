package co.casterlabs.rakurai.json;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.rakurai.json.element.JsonElement;
import co.casterlabs.rakurai.json.element.JsonNumber;
import co.casterlabs.rakurai.json.element.JsonString;
import lombok.NonNull;

public class DefaultTypeResolvers {
    private static final Map<Class<?>, TypeResolver<?>> defaultResolvers = new HashMap<>();

    static {
        register(new TypeResolver<Byte>() {
            @Override
            public @Nullable Byte resolve(@NonNull JsonElement value, @NonNull Class<?> type) {
                if (value.isJsonNull()) {
                    return null;
                } else {
                    return value.getAsNumber().byteValue();
                }
            }

            @Override
            public @Nullable JsonElement writeOut(@NonNull Byte value, @NonNull Class<?> type) {
                return new JsonNumber(value);
            }
        }, Byte.class, byte.class);

        register(new TypeResolver<Short>() {
            @Override
            public @Nullable Short resolve(@NonNull JsonElement value, @NonNull Class<?> type) {
                if (value.isJsonNull()) {
                    return null;
                } else {
                    return value.getAsNumber().shortValue();
                }
            }

            @Override
            public @Nullable JsonElement writeOut(@NonNull Short value, @NonNull Class<?> type) {
                return new JsonNumber(value);
            }
        }, Short.class, short.class);

        register(new TypeResolver<Integer>() {
            @Override
            public @Nullable Integer resolve(@NonNull JsonElement value, @NonNull Class<?> type) {
                return value.getAsNumber().intValue();
            }

            @Override
            public @Nullable JsonElement writeOut(@NonNull Integer value, @NonNull Class<?> type) {
                return new JsonNumber(value);
            }
        }, Integer.class, int.class);

        register(new TypeResolver<Long>() {
            @Override
            public @Nullable Long resolve(@NonNull JsonElement value, @NonNull Class<?> type) {
                if (value.isJsonNull()) {
                    return null;
                } else {
                    return value.getAsNumber().longValue();
                }
            }

            @Override
            public @Nullable JsonElement writeOut(@NonNull Long value, @NonNull Class<?> type) {
                return new JsonNumber(value);
            }
        }, Long.class, long.class);

        register(new TypeResolver<Float>() {
            @Override
            public @Nullable Float resolve(@NonNull JsonElement value, @NonNull Class<?> type) {
                if (value.isJsonNull()) {
                    return null;
                } else {
                    return value.getAsNumber().floatValue();
                }
            }

            @Override
            public @Nullable JsonElement writeOut(@NonNull Float value, @NonNull Class<?> type) {
                return new JsonNumber(value);
            }
        }, Float.class, float.class);

        register(new TypeResolver<Double>() {
            @Override
            public @Nullable Double resolve(@NonNull JsonElement value, @NonNull Class<?> type) {
                if (value.isJsonNull()) {
                    return null;
                } else {
                    return value.getAsNumber().doubleValue();
                }
            }

            @Override
            public @Nullable JsonElement writeOut(@NonNull Double value, @NonNull Class<?> type) {
                return new JsonNumber(value);
            }
        }, Double.class, double.class);

        register(new TypeResolver<Character>() {
            @Override
            public @Nullable Character resolve(@NonNull JsonElement value, @NonNull Class<?> type) {
                if (value.isJsonNull()) {
                    return null;
                } else {
                    return (char) value.getAsNumber().intValue();
                }
            }

            @Override
            public @Nullable JsonElement writeOut(@NonNull Character value, @NonNull Class<?> type) {
                return new JsonNumber((int) value);
            }
        }, Character.class, char.class);

        register(new TypeResolver<String>() {
            @Override
            public @Nullable String resolve(@NonNull JsonElement value, @NonNull Class<?> type) {
                if (value.isJsonNull()) {
                    return null;
                } else {
                    return value.getAsString();
                }
            }

            @Override
            public @Nullable JsonElement writeOut(@NonNull String value, @NonNull Class<?> type) {
                return new JsonString(value);
            }
        }, String.class);
    }

    private static void register(TypeResolver<?> resolver, Class<?>... types) {
        for (Class<?> type : types) {
            defaultResolvers.put(type, resolver);
        }
    }

    public static Map<Class<?>, TypeResolver<?>> get() {
        return new HashMap<>(defaultResolvers);
    }

}
