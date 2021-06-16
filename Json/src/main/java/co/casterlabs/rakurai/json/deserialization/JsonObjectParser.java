package co.casterlabs.rakurai.json.deserialization;

import static co.casterlabs.rakurai.CharStrings.*;

import co.casterlabs.rakurai.json.Rson.RsonConfig;
import co.casterlabs.rakurai.json.element.JsonObject;
import co.casterlabs.rakurai.json.serialization.JsonParseException;
import lombok.NonNull;

public class JsonObjectParser extends JsonParser {

    @Override
    public ParsedTokenPair readToken(char[] in, int skip, @NonNull RsonConfig settings) throws JsonParseException, JsonLexException {
        int sectionLength = 0;
        boolean startFound = false;

        int level = 0;
        boolean isStringEscaped = false;
        boolean inString = false;
        while (true) {
            int pos = sectionLength + skip;

            if (pos >= in.length) {
                if (startFound) {
                    throw new JsonParseException("Cannot find end of object: " + new String(in));
                } else {
                    throw new JsonLexException();
                }
            } else {
                char c = in[pos];

                sectionLength++;

                boolean isQuote = (c == '"') || (settings.areJson5FeaturesEnabled() && (c == '\''));

                if (isQuote && !isStringEscaped) {
                    inString = !inString;
                } else if (!inString) {
                    if ((c == '[') && !startFound) {
                        // We've stumbled on an array.
                        throw new JsonLexException();
                    } else if (c == '{') {
                        level++;
                        startFound = true;
                    } else if (!startFound && (strfindex(JsonParser.JSON_WHITESPACE, c) == -1)) {
                        throw new JsonLexException();
                    } else if (c == '}') {
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

        int dSkip = strfindex(section, '{') + 1;
        int dLen = strlindex(section, '}') + 1;

        int objectContentsLen = dLen - dSkip;
        char[] objectContents = new char[objectContentsLen];
        strcpy(section, objectContents, dSkip);

        JsonObject object = new JsonObject();

        int read = 0;
        while (read < objectContentsLen - 1) {
            int colonLocation = strfindex(objectContents, ':', read);

            if (colonLocation == -1) {
                break;
            } else {
                int keyLen = colonLocation - read;

                if (keyLen < -1) {
                    break;
                }

                char[] keyContents = new char[keyLen];
                strcpy(objectContents, keyContents, read);

                read += keyLen + 1; // Include the colon.

                String key;

                try {
                    key = JsonStringParser.readObjectKey(keyContents, settings);
                } catch (JsonLexException e) {
                    if (settings.areJson5FeaturesEnabled()) {
                        if (!strcontainsany(keyContents, JsonStringParser.NEEDS_ESCAPE)) {
                            key = new String(keyContents);
                        }
                    }

                    throw new JsonParseException("Cannot make heads or tails of object key: " + new String(keyContents));
                }

                ParsedTokenPair pair = JsonParser.parseElement(objectContents, read, settings);

                if (pair == null) {
                    read++; // Was a dud.
                } else {
                    read += pair.getRead() + 1; // Returned value will always be one less

                    object.put(key, pair.getElement());
                }
            }
        }

        return new ParsedTokenPair(object, sectionLength);
    }

}
