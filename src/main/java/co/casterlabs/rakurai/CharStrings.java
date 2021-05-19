package co.casterlabs.rakurai;

public class CharStrings {

    public static void strcpy(char[] source, char[] dest) {
        System.arraycopy(source, 0, dest, 0, dest.length);
    }

    public static void strcpy(char[] source, char[] dest, int skip) {
        System.arraycopy(source, skip, dest, 0, dest.length);
    }

    public static boolean strcontainsany(char[] str, char[] seek) {
        for (int idx = 0; idx < str.length; idx++) {
            for (int skidx = 0; skidx < seek.length; skidx++) {
                if (str[idx] == seek[skidx]) {
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean strequals(char[] s1, char[] s2) {
        if (s1.length == s2.length) {
            for (int idx = 0; idx < s1.length; idx++) {
                if (s1[idx] != s2[idx]) {
                    return false;
                }
            }

            return true;
        } else {
            return false;
        }
    }

    public static boolean strcontainsonly(char[] str, char[] valid) {
        for (int idx = 0; idx < str.length; idx++) {
            if (strfindex(valid, str[idx]) == -1) {
                return false;
            }
        }

        return true;
    }

    public static char[] strdropchars(char[] str, char[] drop) {
        char[] buf = new char[str.length];
        int bufLen = 0;

        for (int idx = 0; idx < str.length; idx++) {
            char c = str[idx];

            if (strfindex(drop, c) == -1) {
                buf[bufLen] = c;
                bufLen++;
            }
        }

        char[] result = new char[bufLen];

        strcpy(buf, result);

        return result;
    }

    public static int strfindex(char[] str, char seek) {
        return strfindex(str, seek, 0);
    }

    public static int strfindex(char[] str, char seek, int skip) {
        for (int idx = skip; idx < str.length; idx++) {
            if (str[idx] == seek) {
                return idx;
            }
        }

        return -1;
    }

    public static int strlindex(char[] str, char seek) {
        for (int idx = str.length - 1; idx >= 0; idx--) {
            if (str[idx] == seek) {
                return idx;
            }
        }

        return -1;
    }

}
