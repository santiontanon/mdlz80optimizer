/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package parser;

import cl.MDLConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

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
    }

    
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
        
        StringTokenizer st = new StringTokenizer(line, " \r\n\t()[]#$,;:+-*/%|&'\"!?<>=~^{}", true);
        String previous = null;
        while(st.hasMoreTokens()) {
            String next = st.nextToken();
            if (previous != null) {
                if (doubleTokens.contains(previous+next)) {
                    tokens.remove(tokens.size()-1);
                    tokens.add(previous+next);
                    previous = previous+next;
                    continue;
                }
                if (previous.equals("#") && isHexCharacter(next.charAt(0))) {
                    // merge, as this is just a single symbol
                    tokens.remove(tokens.size()-1);
                    tokens.add(previous+next);
                    previous = previous+next;
                    continue;
                }
                if (previous.equals("$") && isHexCharacter(next.charAt(0))) {
                    // merge, as this is just a single symbol
                    tokens.remove(tokens.size()-1);
                    tokens.add(previous+next);
                    previous = previous+next;
                    continue;
                }
            }
            if (previous != null && Tokenizer.isSingleLineComment(previous)) {
                String token = previous + next;
                while(st.hasMoreTokens()) token += st.nextToken();
                tokens.remove(tokens.size()-1);
                tokens.add(token);
            } else if (next.equals("\"")) {
                String token = next;
                while(st.hasMoreTokens()) {
                    next = st.nextToken();
                    token += next;
                    if (next.equals("\"")) {
                        break;
                    }
                }
                if (token.length()<2 || !token.endsWith("\"")) return null;
                
                token = token.replace("\\n", "\n");
                token = token.replace("\\r", "\r");
                token = token.replace("\\t", "\t");
                token = token.replace("\\\"", "\"");
                token = token.replace("\\\'", "\'");
                tokens.add(token);
            } else if (next.equals("'")) {
                String token = next;
                while(st.hasMoreTokens()) {
                    next = st.nextToken();
                    token += next;
                    if (next.equals("'")) {
                        break;
                    }
                }
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
        return false;
    }
    
    public static boolean isInteger(String token)
    {
        try{
            Integer.parseInt(token);
            return true;
        } catch(Exception e) {
            return false;
        }
    }

    public static boolean isDouble(String token)
    {
        try{
            Double.parseDouble(token);
            return true;
        } catch(Exception e) {
            return false;
        }
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
        
        if (token.charAt(0) == '#' || token.charAt(0) == '$') startIndex = 1;
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
    
    
    public static String toHexWord(int value)
    {
        String allowed = "0123456789abcdef";
        String hex = "";
        while(value < 0) value += 0x10000;
        value = value % 0x10000;
        while(value != 0) {
            int digit = value%16;
            value = value/16;
            hex = allowed.charAt(digit) + hex;
        }
        while(hex.length() < 4) {
            hex = "0" + hex;
        }
        
        return hex;
    }

    
    public static String toHexWord(int value, int hex_style)
    {
        switch (hex_style) {
            case MDLConfig.HEX_STYLE_HASH:
                return "#" + toHexWord(value);
            case MDLConfig.HEX_STYLE_HASH_CAPS:
                return "#" + toHexWord(value).toUpperCase();
            case MDLConfig.HEX_STYLE_H:
            {
                String hex = Tokenizer.toHexWord(value);
                if (hex.charAt(0) >= 'a' && hex.charAt(0) <= 'f') {
                    hex = "0" + hex;
                }
                return hex + "h";
            }
            case MDLConfig.HEX_STYLE_H_CAPS:
            {
                String hex = Tokenizer.toHexWord(value);
                if (hex.charAt(0) >= 'a' && hex.charAt(0) <= 'f') {
                    hex = "0" + hex;
                }
                return (hex + "h").toUpperCase();
            }
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
