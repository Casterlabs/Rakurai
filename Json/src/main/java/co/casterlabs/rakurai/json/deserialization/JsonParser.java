package co.casterlabs.rakurai.json.deserialization;

import co.casterlabs.rakurai.json.Rson.RsonConfig;
import co.casterlabs.rakurai.json.element.JsonArray;
import co.casterlabs.rakurai.json.element.JsonElement;
import co.casterlabs.rakurai.json.element.JsonObject;
import co.casterlabs.rakurai.json.serialization.JsonParseException;
import lombok.NonNull;

public abstract class JsonParser {
    protected static final char[] JSON_END_TOKENS = ",}]".toCharArray();
    protected static final char[] JSON_WHITESPACE = "\n\t ".toCharArray();

    private static final JsonParser[] tokenizers = new JsonParser[] {
            new JsonDudParser(),
            new JsonBooleanParser(),
            new JsonNumberParser(),
            new JsonNullParser(),
            new JsonStringParser(),
            new JsonObjectParser(),
            new JsonArrayParser()
    };

    public abstract ParsedTokenPair readToken(char[] in, int skip, @NonNull RsonConfig settings) throws JsonParseException, JsonLexException;

    protected static ParsedTokenPair parseElement(char[] in, int skip, @NonNull RsonConfig settings) throws JsonParseException {
        for (JsonParser tokenizer : tokenizers) {
            try {
                return tokenizer.readToken(in, skip, settings);
            } catch (JsonLexException ignored) {}
        }

        throw new JsonParseException(String.format("Unknown input: %s", new String(in).substring(skip)));
    }

    public static JsonElement parseString(@NonNull String json, @NonNull RsonConfig settings) throws JsonParseException {
        json = json.trim();

        char start = json.charAt(0);
        char end = json.charAt(json.length() - 1);

        ParsedTokenPair pair = parseElement(json.toCharArray(), 0, settings);

        if (pair == null) {
            return null;
        } else {
            JsonElement e = pair.getElement();

            if (((start == '{') && (!(e instanceof JsonObject) || (end != '}')))
                || ((end == '}') && start != ('{'))) {
                throw new JsonParseException("Cannot parse object: " + json);
            } else if ((start == '[') && (!(e instanceof JsonArray) || (end != ']'))
                || ((end == ']') && start != ('['))) {
                    System.out.printf("%s %s\n", e.getClass().getSimpleName(), e);
                    throw new JsonParseException("Cannot parse array: " + json);
                }

            return e;
        }
    }

}
