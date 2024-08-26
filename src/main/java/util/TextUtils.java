package util;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.StringTokenizer;

import org.apache.commons.lang3.tuple.Pair;

/**
 * Convenience utility class with text-related utilities
 */
public class TextUtils {

    /**
     * Compares a string against a pattern allowing wildcards ("?", "*") in a case-sensitive manner
     * @param pattern the pattern to check as a string
     * @param string the string to check against the pattern
     * @return {@code true} if the string matches the pattern
     */
    public static boolean matches(String pattern, String string) {
        return matches(pattern, string, true);
    }

    /**
     * Compares a string against a pattern allowing wildcards ("?", "*") in a case-insensitive manner
     * @param pattern the pattern to check as a string
     * @param string the string to check against the pattern
     * @return {@code true} if the string matches the pattern
     */
    public static boolean matchesIgnoreCase(String pattern, String string) {
        return matches(pattern, string, false);
    }

    /**
     * Compares a string against a pattern allowing wildcards ("?", "*")
     * @param pPattern the pattern to check as a string
     * @param pString the string to check against the pattern
     * @param caseSensitive to perform the comparison in a case-sensitive manner
     * @return {@code true} if the string matches the pattern
     */
    public static boolean matches(String pPattern, String pString, boolean caseSensitive) {

        // (sanity check)
        if (pPattern == null || pPattern.isEmpty()) {
            return pString == null || pString.isEmpty();
        }

        // (sanitizes input)
        String pattern = pPattern;  // cannot be null here
        String string = (pString == null ? "":pString);

        if (!caseSensitive) {
            pattern = pattern.toLowerCase();
            string = string.toLowerCase();
        }
        // Checks for every literal in proper order
        int pos = 0;
        StringTokenizer t = new StringTokenizer(pattern, "*");
        while(t.hasMoreTokens()) {
            String literal = t.nextToken();
            final int nextPos = string.indexOf(literal, pos);
            if (nextPos < 0) {
                return false;
            }
            pos = nextPos + literal.length();
        }        
        return true;
    }


    /**
     * Compares strings against a pattern allowing wildcards ("?", "*") in a case-sensitive manner
     * @param pattern the pattern to check as a string
     * @param strings the strings to check against the pattern
     * @return {@code true} if at least one of the strings matches the pattern
     */
    public static boolean anyMatches(String pattern, String ... strings) {
        return (strings != null)
                && anyMatches(pattern, Arrays.asList(strings));
    }

    /**
     * Compares strings against a pattern allowing wildcards ("?", "*") in a case-insensitive manner
     * @param pattern the pattern to check as a string
     * @param strings the strings to check against the pattern
     * @return {@code true} if at least one of the strings matches the pattern
     */
    public static boolean anyMatchesIgnoreCase(String pattern, String ... strings) {
        return (strings != null)
                && anyMatchesIgnoreCase(pattern, Arrays.asList(strings));
    }

    /**
     * Compares strings against a pattern allowing wildcards ("?", "*") in a case-sensitive manner
     * @param pattern the pattern to check as a string
     * @param strings the strings to check against the pattern
     * @return {@code true} if at least one of the strings matches the pattern
     */
    public static boolean anyMatches(String pattern, Collection<String> strings) {
        return anyMatches(pattern, strings, true);
    }

    /**
     * Compares strings against a pattern allowing wildcards ("?", "*") in a case-insensitive manner
     * @param pattern the pattern to check as a string
     * @param strings the strings to check against the pattern
     * @return {@code true} if at least one of the strings matches the pattern
     */
    public static boolean anyMatchesIgnoreCase(String pattern, Collection<String> strings) {
        return anyMatches(pattern, strings, false);
    }

    private static boolean anyMatches(String pattern, Collection<String> strings, boolean caseSensitive) {

        // (sanity check)
        if (strings == null) {
            return false;
        }

        // Checks every string for a positive match
        for (String string: strings) {
            if (caseSensitive) {
                if (matches(pattern, string)) {
                    return true;
                }
            } else {
                if (matchesIgnoreCase(pattern, string)) {
                    return true;
                }
            }
        }
        return false;
    }


    private TextUtils() {
        super();
    }
    
    
    public static Pair<String, String> splitFileNameExtension(String fileName) {
        for(int i = fileName.length()-1; i >= 0; i--) {
            if (fileName.charAt(i) == '.') {
                return Pair.of(fileName.substring(0, i), fileName.substring(i));
            } else if (fileName.charAt(i) == File.separatorChar) {
                break;
            }
        }
        
        return Pair.of(fileName, "");
    }

}