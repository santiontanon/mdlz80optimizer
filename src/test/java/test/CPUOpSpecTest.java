/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import cl.MDLConfig;
import code.CodeBase;
import code.SourceFile;
import code.CodeStatement;
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
    private final MDLConfig config;
    private final CodeBase code;

    public CPUOpSpecTest() {
        config = new MDLConfig();
        code = new CodeBase(config);   
    }

    @Test public void test1() throws IOException { Assert.assertEquals("ld a, 1", test("ld a,1", null)); }
    @Test public void test2() throws IOException { Assert.assertEquals("add a, 1", test("add 1", null)); }
    @Test public void test3() throws IOException { Assert.assertEquals("ex de, hl", test("EX HL, DE", null)); }
    @Test public void test4() throws IOException { Assert.assertEquals(null, test("EX HL, BC", null)); }
    @Test public void test5() throws IOException { Assert.assertEquals(null, test("ld a,(hl++)", null)); }
    @Test public void test6() throws IOException { Assert.assertEquals("ld a, (hl)\ninc hl", test("ld a,(hl++)", "sjasm")); }
    @Test public void test7() throws IOException { Assert.assertEquals("dec ix\nld c, (ix + 1)", test("ld c,(--ix+1)", "sjasm")); }
    @Test public void test8() throws IOException { Assert.assertEquals("ld (ix + (3 + 4)), l", test("ld (ix+(3+4)),l", null)); }
    @Test public void test9() throws IOException { Assert.assertEquals("ex af, af'", test("EX AF, AF", null)); }
    @Test public void test10() throws IOException { Assert.assertEquals("ld d, b\nld e, c", test("ld de, bc", "sjasm")); }
    @Test public void test11() throws IOException { Assert.assertEquals("ld e, (hl)\ninc hl\nld d, (hl)\ndec hl", test("ld de, (hl)", "sjasm")); }
    @Test public void test12() throws IOException { Assert.assertEquals("push hl\npop ix", test("ld ix, hl", "sjasm")); }
    @Test public void test13() throws IOException { Assert.assertEquals("ld c, (ix + (3 + 4))\nld b, (ix + (3 + 4) + 1)", test("ld bc,(ix+(3+4))", "sjasm")); }
    @Test public void test14() throws IOException { Assert.assertEquals("sbc a, (ix + 1)", test("sbc (ix+1)", null)); }
    @Test public void test15() throws IOException { Assert.assertEquals("pop bc\npop af\npop hl", test("pop bc,af,hl", "sjasm")); }
    @Test public void test16() throws IOException { Assert.assertEquals("ld (iy), c\ninc iy\nld (iy), b\ninc iy", test("ldi (IY),BC", "sjasm")); }    
    @Test public void test17() throws IOException { Assert.assertEquals("ld (iy + 1), c\ninc iy\nld (iy + 1), b\ninc iy", test("ldi (IY+1),BC", "sjasm")); }    
    @Test public void test18() throws IOException { Assert.assertEquals("ld (ix), d\ninc ix", test("ldi (IX),D", "sjasm")); }    
    @Test public void test19() throws IOException { Assert.assertEquals("ld (hl), 1000\ninc hl", test("ldi (HL),1000", "sjasm")); }    
    @Test public void test20() throws IOException { Assert.assertEquals("jp m, 1", test("jp s,1", null)); }

    private String test(String opString, String dialect) throws IOException
    {
        if (dialect == null) {
            Assert.assertTrue(config.parseArgs("dummy.asm"));
        } else {
            Assert.assertTrue(config.parseArgs("dummy.asm","-dialect",dialect));
        }
        SourceFile dummy = new SourceFile("dummy.asm", null, null, code, config);
        
        List<CodeStatement> l = config.lineParser.parse(Tokenizer.tokenize(opString), new SourceLine("", dummy, 0), dummy, 0, code, config);
        if (l == null || l.isEmpty()) return null;
        String output = null;
        for(CodeStatement s:l) {
            if (s == null || s.type != CodeStatement.STATEMENT_CPUOP ||
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
