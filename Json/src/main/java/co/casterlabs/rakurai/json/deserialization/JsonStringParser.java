package co.casterlabs.rakurai.json.deserialization;

import static co.casterlabs.rakurai.CharStrings.*;

import co.casterlabs.rakurai.json.JsonUtil;
import co.casterlabs.rakurai.json.element.JsonString;
import co.casterlabs.rakurai.json.serialization.JsonParseException;

public class JsonStringParser extends JsonParser {
    public static final char[] NEEDS_ESCAPE = "\b\f\n\r\t\u000b".toCharArray();
    private static final char[] QUOTES = "\"'".toCharArray();

    @Override
    public ParsedTokenPair readToken(char[] in, int skip, boolean json5Enabled) throws JsonParseException, JsonLexException {
        int sectionSkip = -1;
        int sectionLength = 0;
        char quote = '_';

        for (int i = 0; i < in.length; i++) {
            char c = in[i + skip];

            if (strfindex(QUOTES, c) != -1) {
                sectionSkip = i + 1;
                sectionLength = i;
                quote = c;
                break;
            } else if (strfindex(JsonParser.JSON_WHITESPACE, c) == -1) {
                throw new JsonLexException();
            }
        }

        boolean escaped = false;
        while (true) {
            char c = in[sectionLength + skip];

            sectionLength++;

            if ((sectionLength > sectionSkip) && !escaped && (c == quote)) {
                break;
            } else {
                if (sectionLength + skip == in.length) {
                    break;
                }
            }

            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            }
        }

        char[] section = new char[sectionLength];
        strcpy(in, section, skip);

        if ((quote == '_') || ((quote == '\'') && !json5Enabled)) {
            throw new JsonLexException();
        } else {
            int endOfStr = strlindex(section, quote);
            int contentsLength = endOfStr - sectionSkip;

            if (contentsLength == -1) {
                throw new JsonLexException();
            } else {
                // Check for trailing junk after quote.
                for (int i = endOfStr + 1; i < section.length; i++) {
                    if (strfindex(JsonParser.JSON_WHITESPACE, section[i]) == -1) {
                        throw new JsonLexException();
                    }
                }

                char[] contents = new char[contentsLength];
                strcpy(section, contents, sectionSkip);

                if (contents.length > 0) {
                    char last = contents[contents.length - 1];
                    char secondToLast = (contents.length > 1) ? contents[contents.length - 2] : ' ';

                    if ((last == '\\') && (secondToLast != '\\')) {
                        throw new JsonParseException("Cannot find end of string: " + new String(section));
                    }
                }

                // Need to re-do escape logic.
                if (strcontainsany(contents, NEEDS_ESCAPE)) {
                    throw new JsonParseException("Unescaped characters in string: " + new String(contents));
                } else {
                    String str = JsonUtil.jsonUnescape(new String(contents));

                    return new ParsedTokenPair(new JsonString(str), sectionLength);
                }
            }
        }
    }

    public static String readObjectKey(char[] section, boolean json5Enabled) throws JsonParseException, JsonLexException {
        char quote = section[0];

        if ((quote == '_') || ((quote == '\'') && !json5Enabled)) {
            throw new JsonLexException();
        } else {
            int endOfStr = strlindex(section, quote);
            int contentsLength = endOfStr - 1; // Skip the trailing quote

            if (contentsLength < 0) {
                throw new JsonLexException();
            } else {
                // Check for trailing junk after quote.
                for (int i = endOfStr + 1; i < section.length; i++) {
                    if (strfindex(JsonParser.JSON_WHITESPACE, section[i]) == -1) {
                        throw new JsonLexException();
                    }
                }

                char[] contents = new char[contentsLength];
                strcpy(section, contents, 1); // We skip the leading quote

                if (contents.length > 0) {
                    char last = contents[contents.length - 1];
                    char secondToLast = (contents.length > 1) ? contents[contents.length - 2] : ' ';

                    if ((last == '\\') && (secondToLast != '\\')) {
                        throw new JsonParseException("Cannot find end of string: " + new String(section));
                    }
                }

                // Need to re-do escape logic.
                if (strcontainsany(contents, NEEDS_ESCAPE)) {
                    throw new JsonParseException("Unescaped characters in string: " + new String(contents));
                } else {
                    return JsonUtil.jsonUnescape(new String(contents));
                }
            }
        }
    }

}
