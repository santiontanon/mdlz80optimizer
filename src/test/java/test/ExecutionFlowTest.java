/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package test;

import cl.MDLConfig;
import cl.MDLLogger;
import code.CodeBase;
import code.CodeStatement;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import workers.pattopt.ExecutionFlowAnalysis;
import workers.pattopt.ExecutionFlowAnalysis.StatementTransition;

/**
 *
 * @author santi
 */
public class ExecutionFlowTest {
    private final MDLConfig config;
    private final CodeBase code;

    public ExecutionFlowTest() {
        config = new MDLConfig();
        code = new CodeBase(config);   
    }
    
    /*
    In these test cases, we mark each "ret" statement in the source code with an ID, and
    with the number of possible places it returns to, which are also then marked in 
    the code with ID-DESTINATION. Then we check that the ExecutionFlowAnalysis
    module can reconstruct those expected results.
    */

    @Test public void test1() throws IOException { test("data/flowtests/test1.asm", null, 1); }
    @Test public void test2() throws IOException { test("data/flowtests/test2.asm", null, 2); }
    @Test public void test3() throws IOException { test("data/flowtests/test3.asm", null, 0); }
    @Test public void test4() throws IOException { test("data/flowtests/test4-exported.asm", "macro80", 1); }
    @Test public void test5() throws IOException { test("data/flowtests/test5-sp.asm", null, 1); }
    @Test public void test6() throws IOException { test("data/flowtests/test6.asm", null, 2); }
    @Test public void test7() throws IOException { test("data/flowtests/test7-rst.asm", null, 1); }
    @Test public void test8() throws IOException { test("data/flowtests/test8.asm", null, 1); }
    @Test public void test9() throws IOException { test("data/flowtests/test9.asm", null, 1); }
    @Test public void test10() throws IOException { test("data/flowtests/test10.asm", null, 2); }
    @Test public void test10b() throws IOException { test("data/flowtests/test10b.asm", null, 2); }
    @Test public void test11() throws IOException { test("data/flowtests/test11-jumptables.asm", null, 4); }
    @Test public void test12() throws IOException { test("data/flowtests/test12-jumptables2.asm", null, 3); }
    @Test public void test13() throws IOException { test("data/flowtests/test13-jumptables3.asm", null, 3); }
    @Test public void test13b() throws IOException { test("data/flowtests/test13-jumptables3b.asm", null, 4); }
    @Test public void test14() throws IOException { test("data/flowtests/test14-jumptables.asm", null, 4); }
    @Test public void test14b() throws IOException { test("data/flowtests/test14-jumptables2.asm", null, 4); }

    
    private void test(String inputFile, String dialect, int nRets) throws IOException
    {
        if (dialect == null) {
            Assert.assertTrue(config.parseArgs(inputFile));
        } else {
            Assert.assertTrue(config.parseArgs(inputFile, "-dialect", dialect));
        }

        Assert.assertTrue(
                "Could not parse file " + inputFile,
                config.codeBaseParser.parseMainSourceFiles(config.inputFiles, code));        
        config.logger.setMinLevelToLog(MDLLogger.DEBUG);
        ExecutionFlowAnalysis flowAnalyzer = new ExecutionFlowAnalysis(code, config);
        HashMap<CodeStatement, List<StatementTransition>> table = flowAnalyzer.findAllRetDestinations();
        
        for(CodeStatement s:table.keySet()) {
            config.info("   ret analyzed: " + s);
        }
        Assert.assertEquals(nRets, table.size());
        
        for(CodeStatement s: table.keySet()) {
            Assert.assertNotNull(s.comment);
            String tokens[] = s.comment.split(" ");
            String ID = tokens[1];
            int nDestinations = Integer.parseInt(tokens[2]);
            Assert.assertEquals(nDestinations, table.get(s).size());
            for(StatementTransition sd: table.get(s)) {
                Assert.assertNotNull(sd.s.comment);
                Assert.assertTrue(sd.s.comment.contains(ID+"-DESTINATION"));
            }
        }                
    }    
}
