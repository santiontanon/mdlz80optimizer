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

    @Test public void test1() throws IOException { Assert.assertEquals("ld a, 1", test("ld a,1")); }
    @Test public void test2() throws IOException { Assert.assertEquals("add a, 1", test("add 1")); }
    @Test public void test3() throws IOException { Assert.assertEquals("ex de, hl", test("EX HL, DE")); }
    @Test public void test4() throws IOException { Assert.assertEquals(null, test("EX HL, BC")); }
    
    private String test(String opString) throws IOException
    {
        Assert.assertTrue(mdlConfig.parseArgs("dummy.asm"));
        SourceFile dummy = new SourceFile("dummy.asm", null, null, mdlConfig);
        
        List<SourceStatement> l = mdlConfig.lineParser.parse(Tokenizer.tokenize(opString), new SourceLine("", dummy, 0), dummy, codeBase, mdlConfig);
        if (l == null || l.size() != 1) return null;
        SourceStatement s = l.get(0);
        if (s == null || s.type != SourceStatement.STATEMENT_CPUOP ||
            s.op == null) return null;
        return s.op.toString();
    }    
}
