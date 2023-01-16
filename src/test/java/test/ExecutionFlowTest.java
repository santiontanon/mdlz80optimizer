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

    @Test public void test1() throws IOException { test("data/flowtests/test1.asm", null); }

    
    private void test(String inputFile, String dialect) throws IOException
    {
        if (dialect == null) {
            Assert.assertTrue(config.parseArgs(inputFile));
        } else {
            Assert.assertTrue(config.parseArgs(inputFile, "-dialect", dialect));
        }

        Assert.assertTrue(
                "Could not parse file " + inputFile,
                config.codeBaseParser.parseMainSourceFiles(config.inputFiles, code));        
        
//        config.logger.setMinLevelToLog(MDLLogger.DEBUG);
        ExecutionFlowAnalysis flowAnalyzer = new ExecutionFlowAnalysis(code, config);
        HashMap<CodeStatement, List<CodeStatement>> table = flowAnalyzer.findAllRetDestinations();
        Assert.assertEquals(1, table.size());
        
        for(CodeStatement s: table.keySet()) {
            Assert.assertNotNull(s.comment);
            String tokens[] = s.comment.split(" ");
            String ID = tokens[1];
            int nDestinations = Integer.parseInt(tokens[2]);
            Assert.assertEquals(nDestinations, table.get(s).size());
            for(CodeStatement sd: table.get(s)) {
                Assert.assertNotNull(sd.comment);
                Assert.assertTrue(sd.comment.contains(ID+"-DESTINATION"));
            }
        }                
    }    
}
