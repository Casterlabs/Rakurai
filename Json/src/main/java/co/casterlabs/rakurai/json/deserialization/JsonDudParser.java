package co.casterlabs.rakurai.json.deserialization;

import static co.casterlabs.rakurai.CharStrings.*;

import co.casterlabs.rakurai.json.serialization.JsonParseException;

// This is here for empty statements (e.g [123,] contains a second element that is an empty statement)
public class JsonDudParser extends JsonParser {

    @Override
    public ParsedTokenPair readToken(char[] in, int skip, boolean json5Enabled) throws JsonParseException, JsonLexException {
        int length = 0;

        while (true) {
            char c = in[length + skip];

            if (strfindex(JsonParser.JSON_END_TOKENS, c) != -1) {
                break;
            } else {
                length++;

                if (length + skip == in.length) {
                    throw new JsonLexException();
                }
            }
        }

        char[] read = new char[length];
        strcpy(in, read, skip);

        read = strdropchars(read, JsonParser.JSON_WHITESPACE);

        // Trailing commas is a JSON5 feature.
        if ((read.length == 0) && json5Enabled) {
            return null;
        } else {
            throw new JsonLexException();
        }
    }

}
