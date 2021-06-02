package co.casterlabs.rakurai.json.deserialization;

import static co.casterlabs.rakurai.CharStrings.*;

import co.casterlabs.rakurai.json.element.JsonArray;
import co.casterlabs.rakurai.json.serialization.JsonParseException;

public class JsonArrayParser extends JsonParser {

    @Override
    public ParsedTokenPair readToken(char[] in, int skip, boolean json5Enabled) throws JsonParseException, JsonLexException {
        int sectionLength = 0;
        boolean startFound = false;

        int level = 0;
        boolean isStringEscaped = false;
        boolean inString = false;
        while (true) {
            int pos = sectionLength + skip;

            if (pos >= in.length) {
                if (startFound) {
                    throw new JsonParseException("Cannot find end of array: " + new String(in));
                } else {
                    throw new JsonLexException();
                }
            } else {
                char c = in[pos];

                sectionLength++;

                boolean isQuote = (c == '"') || (json5Enabled && (c == '\''));

                if (isQuote && !isStringEscaped) {
                    inString = !inString;
                } else if (!inString) {
                    if ((c == '{') && !startFound) {
                        // We've stumbled on an object.
                        throw new JsonLexException();
                    } else if (c == '[') {
                        level++;
                        startFound = true;
                    } else if (!startFound && (strfindex(JsonParser.JSON_WHITESPACE, c) == -1)) {
                        throw new JsonLexException();
                    } else if (c == ']') {
                        level--;

                        if (level == 0) {
                            break;
                        }
                    }
                }

                if (isStringEscaped) {
                    isStringEscaped = false;
                } else if (c == '\\') {
                    isStringEscaped = true;
                }
            }
        }

        char[] section = new char[sectionLength];
        strcpy(in, section, skip);

        int dSkip = strfindex(section, '[') + 1;
        int dLen = strlindex(section, ']') + 1;

        int arrayContentsLen = dLen - dSkip;
        char[] arrayContents = new char[arrayContentsLen];
        strcpy(section, arrayContents, dSkip);

        JsonArray array = new JsonArray();

        int read = 0;
        while (read < arrayContentsLen - 1) {
            ParsedTokenPair pair = JsonParser.parseElement(arrayContents, read, json5Enabled);

            if (pair == null) {
                if (json5Enabled) {
                    // Was a dud.
                    read++;
                } else {
                    throw new JsonParseException("Empty entry: " + new String(arrayContents));
                }
            } else {
                read += pair.getRead() + 1; // Returned value will always be one less

                array.add(pair.getElement());
            }
        }

        return new ParsedTokenPair(array, sectionLength);
    }

}
