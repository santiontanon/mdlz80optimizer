/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package cl;

import workers.DotGenerator;
import code.CodeBase;
import workers.AnnotatedSourceCodeGenerator;
import workers.BinaryGenerator;
import workers.DataOptimizer;
import workers.Disassembler;
import workers.Help;
import workers.SourceCodeExecution;
import workers.reorgopt.CodeReorganizer;
import workers.pattopt.PatternBasedOptimizer;
import workers.SourceCodeGenerator;
import workers.SourceCodeTableGenerator;
import workers.SymbolTableGenerator;
import workers.TapGenerator;
import workers.searchopt.SearchBasedOptimizer;

public class Main {
    
    public static String VERSION_STRING = "v2.6.1";

    public static void main(String args[]) throws Exception {
        // Set up the MDL configuration:
        MDLConfig config = new MDLConfig();

        // Add the available workers (in the order in which they will be executed):
        config.registerWorker(new Help(config));
        config.registerWorker(new Disassembler(config));
        config.registerWorker(new SearchBasedOptimizer(config));
        config.registerWorker(new CodeReorganizer(config));
        config.registerWorker(new PatternBasedOptimizer(config));
        config.registerWorker(new DataOptimizer(config));
        
        config.registerWorker(new DotGenerator(config));
        config.registerWorker(new SymbolTableGenerator(config));
        config.registerWorker(new SourceCodeTableGenerator(config));
        config.registerWorker(new SourceCodeGenerator(config));
        config.registerWorker(new AnnotatedSourceCodeGenerator(config));
        config.registerWorker(new BinaryGenerator(config));
        config.registerWorker(new TapGenerator(config));
        config.registerWorker(new SourceCodeExecution(config));

        // Parse command line arguments:
        if (!config.parseArgs(args)) {
            config.error("Could not fully parse the arguments (error code 1).");
            exit(1, config);
        }
        
        // If there is nothing to do, just terminate:
        if (!config.somethingToDo()) {
            config.warn("Nothing to do. Please specify some task for MDL to do.");
            return;
        }
        
        // Parse the code base:
        CodeBase code = new CodeBase(config);
        switch(config.codeSource) {
            case MDLConfig.CODE_FROM_INPUT_FILE:
                if (!config.inputFiles.isEmpty() && 
                    !config.codeBaseParser.parseMainSourceFiles(config.inputFiles, code)) {
                    if (config.dialectParser != null) {
                        config.error("Could not fully parse the code (error code 2).");
                    } else {
                        config.error("Could not fully parse the code (error code 2). Maybe missing `-dialect <dialect>` flag?");
                    }
                    exit(2, config);
                }
                break;
            case MDLConfig.CODE_FROM_SEARCHBASEDOPTIMIZER:
                // Code will be generated automatically when the worker is called
                break;
            case MDLConfig.CODE_FROM_DISASSEMBLY:
                // Code will be generated automatically when the worker is called
                break;
            default:
                config.error("Unknown source code source: " + config.codeSource);
                exit(4, config);
        }
        
        // Execute all the requested workers according to the command-line arguments:
        if (!config.executeWorkers(code)) {
            config.error("Could not fully execute some workers (error code 3).");
            exit(3, config);
        }
        
        if (config.optimizerStats.anyOptimization()) config.info("mdl optimization summary: " + config.optimizerStats.summaryString(config));
        config.logger.reportNErrorsAndWarningsIfAny();
    }
    
    
    public static void exit(int error, MDLConfig config) throws Exception {
        config.logger.reportNErrorsAndWarningsIfAny();
        System.exit(error);
    }
}
