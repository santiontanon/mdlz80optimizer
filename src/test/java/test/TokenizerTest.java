/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package test;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import parser.Tokenizer;

/**
 *
 * @author santi
 */
public class TokenizerTest {

    @Test
    public void test()
    {
        int failures = 0;
        failures += tokenizerTest("ld a,2", new String[]{"ld","a",",","2"});
        failures += tokenizerTest("ex af,af'", new String[]{"ex","af",",","af'"});
        failures += tokenizerTest("ex af,AF'", new String[]{"ex","af",",","AF'"});
        failures += tokenizerTest("variable<<2", new String[]{"variable","<<","2"});
        failures += tokenizerTest("ds (($ + 1 - 1) >> 8) != ($ >> 8) && (100H - ($ & 0FFH)) || 0", new String[]{"ds","(","(","$","+","1","-","1",")",">>","8",")","!=","(","$",">>","8",")","&&","(","100H","-","(","$","&","0FFH",")",")","||","0"});
        failures += tokenizerTest(".include", new String[]{".include"});
        failures += tokenizerTest("ld a,(hl)", new String[]{"ld","a",",","(","hl",")"});
        failures += tokenizerTest("ld a,[hl]", new String[]{"ld","a",",","[","hl","]"});
        failures += tokenizerTest("@@my_local_label:", new String[]{"@@my_local_label",":"});
        if (failures > 0) {
            throw new Error(failures + " tests failed!");
        }
    }


    public static int tokenizerTest(String line, String[] expected)
    {
        List<String> tokens = Tokenizer.tokenize(line);
        if (tokens == null  || tokens.size() != expected.length) {
            System.err.println("Expected " + Arrays.toString(expected) + " got " + tokens);
            return 1;
        }
        for(int i = 0;i<tokens.size();i++) {
            if (!tokens.get(i).equals(expected[i])) {
                System.err.println("Expected " + Arrays.toString(expected) + " got " + tokens);
                return 1;
            }
        }
        return 0;
    }
}
