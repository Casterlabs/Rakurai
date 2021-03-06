package co.casterlabs.rakurai;

import lombok.NonNull;

public class StringUtil {
    private static final String EMPTY_STRING = "";

    public static String repeat(@NonNull String repeat, int amount) {
        // Avoid unnecessary allocations.
        if (amount == 0) {
            return EMPTY_STRING;
        } else if (amount == 1) {
            return repeat;
        } else {
            StringBuilder sb = new StringBuilder();

            for (int i = 0; i < amount; i++) {
                sb.append(repeat);
            }

            return sb.toString();
        }
    }

    public static String prettifyHeader(String header) {
        char[] chars = header.toLowerCase().toCharArray();

        chars[0] = Character.toUpperCase(chars[0]);

        // Go ahead and start at 1, since we already replaced char[0].
        for (int i = 1; i < chars.length; i++) {
            if ((chars[i] == '-') && ((i + 1) < chars.length)) {
                chars[i + 1] = Character.toUpperCase(chars[i + 1]);
            }
        }

        return new String(chars);
    }

}
