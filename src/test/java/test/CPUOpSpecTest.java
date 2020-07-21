/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import cl.MDLConfig;
import code.CodeBase;
import code.SourceFile;
import code.SourceStatement;
import java.io.IOException;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import parser.SourceLine;
import parser.Tokenizer;

/**
 *
 * @author santi
 */
public class CPUOpSpecTest {    
    private final MDLConfig mdlConfig;
    private final CodeBase codeBase;

    public CPUOpSpecTest() {
        mdlConfig = new MDLConfig();
        codeBase = new CodeBase(mdlConfig);   
    }

    @Test public void test1() throws IOException { Assert.assertEquals("ld a, 1", test("ld a,1", null)); }
    @Test public void test2() throws IOException { Assert.assertEquals("add a, 1", test("add 1", null)); }
    @Test public void test3() throws IOException { Assert.assertEquals("ex de, hl", test("EX HL, DE", null)); }
    @Test public void test4() throws IOException { Assert.assertEquals(null, test("EX HL, BC", null)); }
    @Test public void test5() throws IOException { Assert.assertEquals(null, test("ld a,(hl++)", null)); }
    @Test public void test6() throws IOException { Assert.assertEquals("ld a, (hl)\ninc hl", test("ld a,(hl++)", "sjasm")); }
    @Test public void test7() throws IOException { Assert.assertEquals("dec ix\nld c, (ix + 1)", test("ld c,(--ix+1)", "sjasm")); }
    @Test public void test8() throws IOException { Assert.assertEquals("ld (ix + (3 + 4)), l", test("ld (ix+(3+4)),l", null)); }
    
    private String test(String opString, String dialect) throws IOException
    {
        if (dialect == null) {
            Assert.assertTrue(mdlConfig.parseArgs("dummy.asm"));
        } else {
            Assert.assertTrue(mdlConfig.parseArgs("dummy.asm","-dialect",dialect));
        }
        SourceFile dummy = new SourceFile("dummy.asm", null, null, mdlConfig);
        
        List<SourceStatement> l = mdlConfig.lineParser.parse(Tokenizer.tokenize(opString), new SourceLine("", dummy, 0), dummy, codeBase, mdlConfig);
        if (l == null || l.isEmpty()) return null;
        String output = null;
        for(SourceStatement s:l) {
            if (s == null || s.type != SourceStatement.STATEMENT_CPUOP ||
                s.op == null) return null;
            if (output == null) {
                output = s.op.toString();
            } else {
                output += "\n" + s.op.toString();
            }
        }
        return output;
    }    
}
