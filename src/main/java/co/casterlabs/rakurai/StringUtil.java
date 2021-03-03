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

}
