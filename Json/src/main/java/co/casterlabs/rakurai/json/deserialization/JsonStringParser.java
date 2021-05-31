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
        return readToken(in, skip, json5Enabled, false);
    }

    public ParsedTokenPair readToken(char[] in, int skip, boolean json5Enabled, boolean whole) throws JsonParseException, JsonLexException {
        int sectionLength = 0;

        {
            char quote = '_';
            int quoteLocation = -1;

            for (int i = skip; i < in.length; i++) {
                char c = in[i];

                if (strfindex(QUOTES, c) != -1) {
                    quote = c;
                    quoteLocation = i;
                    break;
                }
            }

            if ((quote == '_') || ((quote == '\'') && !json5Enabled)) {
                throw new JsonLexException();
            }

            char lastChar = '_';
            boolean endFound = false;

            if (whole) {
                sectionLength = in.length;
            } else {
                while (true) {
                    char c = in[sectionLength + skip];

                    if ((quoteLocation < sectionLength) && (c == quote) && (lastChar != '\\')) {
                        endFound = true;
                    }

                    lastChar = c;

                    if (endFound && strfindex(JsonParser.JSON_END_TOKENS, c) != -1) {
                        break;
                    } else {
                        sectionLength++;

                        if (sectionLength + skip == in.length) {
                            break;
                        }
                    }
                }
            }
        }

        char[] section = new char[sectionLength];
        strcpy(in, section, skip);

        int sectionSkip = -1;
        char quote = '_';

        for (int i = 0; i < section.length; i++) {
            char c = section[i];

            if (strfindex(QUOTES, c) != -1) {
                sectionSkip = i + 1;
                quote = c;
                break;
            } else if (strfindex(JsonParser.JSON_WHITESPACE, c) == -1) {
                throw new JsonLexException();
            }
        }

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
