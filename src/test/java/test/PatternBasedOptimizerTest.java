/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package test;

import java.io.IOException;

import org.junit.Test;

import cl.MDLConfig;
import code.CodeBase;
import workers.SourceCodeGenerator;
import workers.pattopt.PatternBasedOptimizer;

/**
 *
 * @author santi
 */
public class PatternBasedOptimizerTest {

    @Test
    public void test() throws IOException {
        int failures = 0;
        failures += test("data/tests/test1.asm", 3);
        failures += test("data/tests/test2.asm", 1);
        failures += test("data/tests/test3.asm", 0);
        failures += test("data/tests/test4.asm", 0);
        failures += test("data/tests/test5.asm", 0);
        failures += test("data/tests/test6.asm", 2);
        failures += test("data/tests/test7.asm", 2);
        failures += test("data/tests/test8.asm", 2);
        failures += test("data/tests/test9.asm", 0);
        failures += test("data/tests/test10.asm", 2);
        failures += test("data/tests/test11.asm", 3);
        failures += test("data/tests/test12.asm", 2);
        failures += test("data/tests/test13.asm", 0);
        failures += test("data/tests/test14.asm", 1);
        failures += test("data/tests/test15.asm", 2);
        failures += test("data/tests/test16.asm", 5);
        failures += test("data/tests/test17.asm", 4);
        failures += test("data/tests/test18.asm", 3);
        failures += test("data/tests/test19.asm", 2);
        failures += test("data/tests/test20.asm", 4);
        failures += test("data/tests/test21.asm", 3);
        failures += test("data/tests/test22.asm", 2);
        failures += test("data/tests/test23.asm", 1);
        failures += test("data/tests/test24.asm", 0);
        if (failures > 0) {
            throw new Error(failures + " tests failed!");
        } else {
            System.out.println(failures + " tests failed!");
        }
    }

    public static int test(String inputFile, int expected_bytesReduced) throws IOException
    {
        MDLConfig config = new MDLConfig();
        if (!config.parse(inputFile)) return 1;
        CodeBase code = new CodeBase(config);
        if (config.codeBaseParser.parseMainSourceFile(config.inputFile, code) == null) {
            config.error("Could not parse file " + inputFile);
            return 1;
        }
        PatternBasedOptimizer pbo = new PatternBasedOptimizer(config);
        PatternBasedOptimizer.OptimizationResult r = pbo.optimize(code);

        SourceCodeGenerator scg = new SourceCodeGenerator(config);
        String result = scg.sourceFileString(code.getMain());
        System.out.println("\n--------------------------------------");
        System.out.println(result);
        System.out.println("--------------------------------------\n");

        if (r.bytesSaved != expected_bytesReduced) {
            config.error("expected_bytesReduced = " + expected_bytesReduced + " for file " + inputFile + ", but was " + r.bytesSaved);
            return 1;
        }

        return 0;
    }
}
