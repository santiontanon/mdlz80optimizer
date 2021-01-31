/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package parser;

import cl.MDLConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Tokenizer {    
    public static final List<String> doubleTokens = new ArrayList<>();
    static{
        doubleTokens.add("<<");
        doubleTokens.add(">>");
        doubleTokens.add("<=");
        doubleTokens.add(">=");
        doubleTokens.add("&&");
        doubleTokens.add("||");
        doubleTokens.add("af'");
        doubleTokens.add("AF'");
        doubleTokens.add("!=");
        doubleTokens.add("//");
        doubleTokens.add("/*");
        doubleTokens.add("*/");
        doubleTokens.add(":=");
        doubleTokens.add("++");
        doubleTokens.add("--");
        doubleTokens.add("::");
        doubleTokens.add("=:");
        doubleTokens.add("==");
    }    
    
    public static HashMap<String,String> stringEscapeSequences = new HashMap<>();
    
    
    public static boolean allowAndpersandHex = false;
    public static boolean sdccStyleHashMarksForConstants = false;
    public static boolean sdccStyleDollarInLabels = false;
    
    
    static Matcher doubleMatcher = Pattern.compile("[\\x00-\\x20]*[+-]?(((((\\p{Digit}+)(\\.)?((\\p{Digit}+)?)([eE][+-]?(\\p{Digit}+))?)|(\\.((\\p{Digit}+))([eE][+-]?(\\p{Digit}+))?)|(((0[xX](\\p{XDigit}+)(\\.)?)|(0[xX](\\p{XDigit}+)?(\\.)(\\p{XDigit}+)))[pP][+-]?(\\p{Digit}+)))[fFdD]?))[\\x00-\\x20]*")
            .matcher("");        
    
    public static List<String> tokenizeIncludingBlanks(String line) {
        return tokenize(line, new ArrayList<>(), true);
    }
    
    
    public static List<String> tokenize(String line) {
        return tokenize(line, new ArrayList<>(), false);
    }

    public static List<String> tokenize(String line, List<String> tokens) {
        return tokenize(line, tokens, false);
    }
    
    
    public static List<String> tokenize(String line, List<String> tokens, boolean includeBlanks) {
        StringTokenizer st;
        if (sdccStyleDollarInLabels) {
            st = new StringTokenizer(line, " \r\n\t()[]#,;:+-*/%|&'\"!?<>=~^{}\\", true);
        } else {
            st = new StringTokenizer(line, " \r\n\t()[]#$,;:+-*/%|&'\"!?<>=~^{}\\", true);
        }
        String previous = null;
        while(st.hasMoreTokens()) {
            String next = st.nextToken();
            if (previous != null) {
                if (doubleTokens.contains(previous+next)) {
                    tokens.remove(tokens.size()-1);
                    tokens.add(previous.concat(next));
                    previous = previous.concat(next);
                    continue;
                }
                if (!sdccStyleHashMarksForConstants) {
                    if (previous.equals("#") && isHexCharacter(next.charAt(0))) {
                        // merge, as this is just a single symbol
                        tokens.remove(tokens.size()-1);
                        tokens.add(previous.concat(next));
                        previous = previous.concat(next);
                        continue;
                    }
                }
                if (previous.equals("$") && isHexCharacter(next.charAt(0))) {
                    // merge, as this is just a single symbol
                    tokens.remove(tokens.size()-1);
                    tokens.add(previous.concat(next));
                    previous = previous.concat(next);
                    continue;
                }
                if (allowAndpersandHex) {
                    if (previous.equals("&") && isHexCharacter(next.charAt(0))) {
                        // merge, as this is just a single symbol
                        tokens.remove(tokens.size()-1);
                        tokens.add(previous.concat(next));
                        previous = previous.concat(next);
                        continue;
                    }
                }
                if (next.equals("%")) {
                    boolean allPercents = true;
                    for(int i = 0;i<previous.length();i++) {
                        if (previous.charAt(i) != '%') {
                            allPercents = false;
                            break;
                        }
                    }
                    if (allPercents) {
                        tokens.remove(tokens.size()-1);
                        tokens.add(previous.concat(next));
                        previous = previous.concat(next);
                        continue;
                    }
                }
            }
            if (previous != null && Tokenizer.isSingleLineComment(previous)) {
                String token = previous + next;
                while(st.hasMoreTokens()) token += st.nextToken();
                tokens.remove(tokens.size()-1);
                tokens.add(token);
            } else if (next.equals("\"")) {
                StringBuilder tokenBuilder = new StringBuilder(next);
                while(st.hasMoreTokens()) {
                    next = st.nextToken();
                    if (next.equals("\\")) {
                        String nextNext = st.nextToken();
                        // TODO: support escape sequences longer than 1 character:
                        if (stringEscapeSequences.containsKey(nextNext.substring(0, 1))) {
                            tokenBuilder.append(stringEscapeSequences.get(nextNext.substring(0, 1))).append(nextNext.substring(1));
                        } else {
                            tokenBuilder.append(next).append(nextNext);
                        }
                    } else {
                        tokenBuilder.append(next);
                    }
                    if (next.equals("\"")) {
                        break;
                    }
                }
                String token = tokenBuilder.toString();
                if (token.length()<2 || !token.endsWith("\"")) return null;
                tokens.add(token);
            } else if (next.equals("'")) {
                StringBuilder tokenBuilder = new StringBuilder(next);
                while(st.hasMoreTokens()) {
                    next = st.nextToken();
                    tokenBuilder.append(next);
                    if (next.equals("'")) {
                        break;
                    }
                }
                String token = tokenBuilder.toString();
                if (token.length()<2 || !token.endsWith("'")) return null;
                token = "\"" + token.substring(1, token.length()-1) + "\"";
                tokens.add(token);
            } else {
                if (!next.equals(" ") && !next.equals("\r") && !next.equals("\n") && !next.equals("\t")) {
                    tokens.add(next);
                } else {
                    if (includeBlanks) {
                        tokens.add(next);
                    }
                }
            } 
            previous = next;
        }
        return tokens;
    }
    
    
    public static boolean isString(String token)
    {
        if (token.startsWith("\"") && token.endsWith("\"")) return true;
        return false;
    }
    
    
    public static String stringValue(String token)
    {
        return token.substring(1, token.length()-1);
    }
    
    
    public static boolean isSymbol(String token)
    {
        if (token.equalsIgnoreCase("af'")) return true;
        if (token.equals("$")) return true;
        
        int c = token.charAt(0);
        if ((c>='a' && c<='z') || (c>='A' && c<='Z') || c=='_' ||
            c=='.' || c == '@') return true;
        
        if (sdccStyleDollarInLabels && token.charAt(token.length()-1) == '$' &&
            c>='0' && c<='9') {
            return true;
        }
        
        return false;
    }
    
    public static boolean isInteger(String token)
    {
        // From: https://stackoverflow.com/questions/237159/whats-the-best-way-to-check-if-a-string-represents-an-integer-in-java
        // Much faster than regexps, external libraries, or Integer.parseInt:
        if (token == null) {
            return false;
        }
        int length = token.length();
        if (length == 0) {
            return false;
        }
        int i = 0;
        if (token.charAt(0) == '-') {
            if (length == 1) {
                return false;
            }
            i = 1;
        }
        for (; i < length; i++) {
            char c = token.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    public static boolean isDouble(String token)
    {
        return doubleMatcher.reset(token).matches();
    }
    
    
    public static boolean isHexCharacter(int c)
    {
        if (c>='a' && c<='f') return true;
        if (c>='A' && c<='F') return true;
        if (c>='0' && c<='9') return true;
        return false;
    }
    
    
    public static boolean isHex(String token)
    {
        return parseHex(token) != null;
    }

    
    public static Integer parseHex(String token)
    {
        int value = 0;
        int startIndex = 0;
        String allowed = "0123456789abcdef";
        
        if (token.charAt(0) == '#' || token.charAt(0) == '$' || token.charAt(0) == '&') startIndex = 1;
        for(int i = startIndex;i<token.length();i++) {
            char c = (char)token.charAt(i);
            c = Character.toLowerCase(c);
            int idx = allowed.indexOf(c);
            if (idx == -1) {
                if (i == token.length()-1 && c == 'h') return value;
                return null;
            }
            value = value * 16 + idx;
        }
        return value;
    }
    
    
    public static String toHex(int value, int length)
    {
        String allowed = "0123456789abcdef";
        String hex = "";
        value = value & 0xffff;
        while(value != 0) {
            int digit = value%16;
            value = value/16;
            hex = allowed.charAt(digit) + hex;
        }
        while(hex.length() < length) {
            hex = "0" + hex;
        }
        
        return hex;
    }
    
    
    public static String toHexWord(int value, int hex_style)
    {
        switch (hex_style) {
            case MDLConfig.HEX_STYLE_HASH:
                return "#" + toHex(value, 4);
            case MDLConfig.HEX_STYLE_HASH_CAPS:
                return "#" + toHex(value, 4).toUpperCase();
            case MDLConfig.HEX_STYLE_H:
            {
                String hex = Tokenizer.toHex(value, 4);
                if (hex.charAt(0) >= 'a' && hex.charAt(0) <= 'f') {
                    hex = "0" + hex;
                }
                return hex + "h";
            }
            case MDLConfig.HEX_STYLE_H_CAPS:
            {
                String hex = Tokenizer.toHex(value, 4);
                if (hex.charAt(0) >= 'a' && hex.charAt(0) <= 'f') {
                    hex = "0" + hex;
                }
                return (hex + "h").toUpperCase();
            }
            case MDLConfig.HEX_STYLE_0X:
                return "0x" + toHex(value, 4);
            case MDLConfig.HEX_STYLE_0X_CAPS:
                return "0x" + toHex(value, 4).toUpperCase();
            default:
                return null;
        }
    }
    
    
    public static String toHexByte(int value, int hex_style)
    {
        switch (hex_style) {
            case MDLConfig.HEX_STYLE_HASH:
                return "#" + toHex(value, 2);
            case MDLConfig.HEX_STYLE_HASH_CAPS:
                return "#" + toHex(value, 2).toUpperCase();
            case MDLConfig.HEX_STYLE_H:
            {
                String hex = Tokenizer.toHexByte(value, 2);
                if (hex.charAt(0) >= 'a' && hex.charAt(0) <= 'f') {
                    hex = "0" + hex;
                }
                return hex + "h";
            }
            case MDLConfig.HEX_STYLE_H_CAPS:
            {
                String hex = Tokenizer.toHex(value, 2);
                if (hex.charAt(0) >= 'a' && hex.charAt(0) <= 'f') {
                    hex = "0" + hex;
                }
                return (hex + "h").toUpperCase();
            }
            case MDLConfig.HEX_STYLE_0X:
                return "0x" + toHex(value, 2);
            case MDLConfig.HEX_STYLE_0X_CAPS:
                return "0x" + toHex(value, 2).toUpperCase();
            
            default:
                return null;
        }
    }
    
    
    public static boolean isBinary(String token)
    {
        return parseBinary(token) != null;
    }

    
    public static Integer parseBinary(String token)
    {
        int value = 0;
        String allowed = "01";
        
        for(int i = 0;i<token.length();i++) {
            char c = (char)token.charAt(i);
            c = Character.toLowerCase(c);
            int idx = allowed.indexOf(c);
            if (idx == -1) {
                if (i == token.length()-1 && c == 'b') return value;
                return null;
            }
            value = value * 2 + idx;
        }
        return value;
    }    
    
    
    public static String toBin(int value, int length)
    {
        String allowed = "01";
        String bin = "";
        value = value & 0xff;
        while(value != 0) {
            int digit = value%2;
            value = value/2;
            bin = allowed.charAt(digit) + bin;
        }
        while(bin.length() < length) {
            bin = "0" + bin;
        }
        
        return bin;
    }  

    
    public static String toBin(int value, int length, int hex_style)
    {
        switch (hex_style) {
            case MDLConfig.HEX_STYLE_HASH:
            case MDLConfig.HEX_STYLE_H:
            {
                String hex = Tokenizer.toBin(value, length);
                if (hex.charAt(0) >= 'a' && hex.charAt(0) <= 'f') {
                    hex = "0" + hex;
                }
                return hex + "b";
            }
            case MDLConfig.HEX_STYLE_HASH_CAPS:
            case MDLConfig.HEX_STYLE_H_CAPS:
            {
                String hex = Tokenizer.toBin(value, length);
                if (hex.charAt(0) >= 'a' && hex.charAt(0) <= 'f') {
                    hex = "0" + hex;
                }
                return (hex + "B").toUpperCase();
            }
            case MDLConfig.HEX_STYLE_0X:
                return "0b" + toBin(value, length);
            case MDLConfig.HEX_STYLE_0X_CAPS:
                return "0B" + toHex(value, length).toUpperCase();
            default:
                return null;
        }
    }    


    public static boolean isOctal(String token)
    {
        return parseOctal(token) != null;
    }

    
    public static Integer parseOctal(String token)
    {
        int value = 0;
        String allowed = "01234567";
        
        for(int i = 0;i<token.length();i++) {
            char c = (char)token.charAt(i);
            c = Character.toLowerCase(c);
            int idx = allowed.indexOf(c);
            if (idx == -1) {
                if (i == token.length()-1 && c == 'o') return value;
                return null;
            }
            value = value * 2 + idx;
        }
        return value;
    }    
    
    
    public static String toOct(int value)
    {
        String allowed = "01234567";
        String oct = "";
        value = value & 0xff;
        while(value != 0) {
            int digit = value%8;
            value = value/8;
            oct = allowed.charAt(digit) + oct;
        }
        
        return oct;
    }  
    
    
    public static String toOct(int value, int hex_style)
    {
        switch (hex_style) {
            case MDLConfig.HEX_STYLE_HASH:
            case MDLConfig.HEX_STYLE_H:
            {
                String hex = Tokenizer.toOct(value);
                if (hex.charAt(0) >= 'a' && hex.charAt(0) <= 'f') {
                    hex = "0" + hex;
                }
                return hex + "o";
            }
            case MDLConfig.HEX_STYLE_HASH_CAPS:
            case MDLConfig.HEX_STYLE_H_CAPS:
            {
                String hex = Tokenizer.toOct(value);
                if (hex.charAt(0) >= 'a' && hex.charAt(0) <= 'f') {
                    hex = "0" + hex;
                }
                return (hex + "O").toUpperCase();
            }
            case MDLConfig.HEX_STYLE_0X:
                return "0o" + toOct(value);
            case MDLConfig.HEX_STYLE_0X_CAPS:
                return "0O" + toOct(value).toUpperCase();
            default:
                return null;
        }
    }    
    
    
    public static boolean isSingleLineComment(String token) {
        if (token.startsWith(";")) return true;
        if (token.startsWith("//")) return true;
        return false;
    }
    

    public static boolean isMultiLineCommentStart(String token) {
        if (token.equals("/*")) return true;
        if (token.equals("{")) return true;
        return false;
    }
    

    public static boolean isMultiLineCommentEnd(String token) {
        if (token.equals("*/")) return true;
        if (token.equals("}")) return true;
        return false;
    }
}
