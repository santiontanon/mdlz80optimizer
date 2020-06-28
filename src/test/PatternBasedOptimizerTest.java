/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package test;

import cl.MDLConfig;
import code.CodeBase;
import workers.SourceCodeGenerator;
import workers.pattopt.PatternBasedOptimizer;

/**
 *
 * @author santi
 */
public class PatternBasedOptimizerTest {
    public static void main(String args[]) throws Exception
    {
        int failures = 0;
        failures += test("data/tests/test1.asm", 3, args);
        failures += test("data/tests/test2.asm", 1, args);
        failures += test("data/tests/test3.asm", 0, args);
        failures += test("data/tests/test4.asm", 0, args);
        failures += test("data/tests/test5.asm", 0, args);
        failures += test("data/tests/test6.asm", 2, args);
        failures += test("data/tests/test7.asm", 2, args);
        failures += test("data/tests/test8.asm", 2, args);
        failures += test("data/tests/test9.asm", 0, args);
        failures += test("data/tests/test10.asm", 2, args);
        failures += test("data/tests/test11.asm", 3, args);
        failures += test("data/tests/test12.asm", 2, args);
        failures += test("data/tests/test13.asm", 0, args);
        failures += test("data/tests/test14.asm", 1, args);
        failures += test("data/tests/test15.asm", 2, args);
        failures += test("data/tests/test16.asm", 5, args);
        failures += test("data/tests/test17.asm", 4, args);
        failures += test("data/tests/test18.asm", 3, args);
        if (failures > 0) {
            throw new Error(failures + " tests failed!");
        }
    }
    
    
    public static int test(String inputFile, int expected_bytesReduced, String a_args[]) throws Exception
    {
        MDLConfig config = new MDLConfig();
        String args[] = new String[a_args.length+1];
        args[0] = inputFile;
        for(int i = 0;i<a_args.length;i++) {
            args[i+1] = a_args[i];
        }
        if (!config.parse(args)) return 1;
        CodeBase code = new CodeBase(config);
        if (config.codeBaseParser.parseSourceFile(config.inputFile, code, null, null) == null) {
            config.error("Could not parse file " + inputFile);
            return 1;
        }
        PatternBasedOptimizer pbo = new PatternBasedOptimizer(config);
        PatternBasedOptimizer.OptimizationResult r = pbo.optimize(code);
        if (r.bytesSaved != expected_bytesReduced) {
            config.error("expected_bytesReduced = " + expected_bytesReduced + " for file " + inputFile + ", but was " + r.bytesSaved);
            return 1;
        }
        
        SourceCodeGenerator scg = new SourceCodeGenerator(config);
        String result = scg.sourceFileString(code.getMain());
        System.out.println("\n--------------------------------------");
        System.out.println(result);
        System.out.println("--------------------------------------\n");
        
        return 0;
    }
}
