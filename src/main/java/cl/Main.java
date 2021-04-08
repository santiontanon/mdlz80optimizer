/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package cl;

import workers.DotGenerator;
import code.CodeBase;
import workers.AnnotatedSourceCodeGenerator;
import workers.BinaryGenerator;
import workers.reorgopt.CodeReorganizer;
import workers.pattopt.PatternBasedOptimizer;
import workers.SourceCodeGenerator;
import workers.SourceCodeTableGenerator;
import workers.SymbolTableGenerator;

public class Main {
    
    public static String VERSION_STRING = "v1.9";

    public static void main(String args[]) throws Exception {
        // Set up the MDL configuration:
        MDLConfig config = new MDLConfig();

        // Add the available workers (in the order in which they will be executed):
        config.registerWorker(new CodeReorganizer(config));
        config.registerWorker(new PatternBasedOptimizer(config));
        
        config.registerWorker(new DotGenerator(config));
        config.registerWorker(new SymbolTableGenerator(config));
        config.registerWorker(new SourceCodeTableGenerator(config));
        config.registerWorker(new SourceCodeGenerator(config));
        config.registerWorker(new AnnotatedSourceCodeGenerator(config));
        config.registerWorker(new BinaryGenerator(config));

        // Parse command line arguments:
        if (!config.parseArgs(args)) {
            config.error("Could not fully parse the arguments (error code 1).");
            System.exit(1);
        }
        
        // If there is nothing to do, just terminate:
        if (!config.somethingToDo()) {
            config.warn("Nothing to do. Please specify some task for MDL to do.");
            return;
        }

        // Parse the code base:
        CodeBase code = new CodeBase(config);
        if (!config.codeBaseParser.parseMainSourceFile(config.inputFile, code)) {
            if (config.dialectParser != null) {
                config.error("Could not fully parse the code (error code 2).");
            } else {
                config.error("Could not fully parse the code (error code 2). Maybe missing `-dialect <dialect>` flag?");
            }
            System.exit(2);
        }
        
        // Execute all the requested workers according to the command-line arguments:
        if (!config.executeWorkers(code)) {
            config.error("Could not fully execute some workers (error code 3).");
            System.exit(3);
        }
        
        if (config.optimizerStats.anyOptimization()) config.info("mdl optimization summary: " + config.optimizerStats.summaryString(config));
    }
}
