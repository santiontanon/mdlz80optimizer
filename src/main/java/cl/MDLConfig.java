/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package cl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import code.CodeBase;
import parser.CPUOpParser;
import parser.CPUOpSpecParser;
import parser.CodeBaseParser;
import parser.ExpressionParser;
import parser.LineParser;
import parser.PreProcessor;
import parser.Tokenizer;
import parser.dialects.Dialect;
import parser.dialects.Dialects;
import workers.MDLWorker;

public class MDLConfig {
    // constants:
    public static final int HEX_STYLE_HASH = 0;
    public static final int HEX_STYLE_HASH_CAPS = 1;
    public static final int HEX_STYLE_H = 2;
    public static final int HEX_STYLE_H_CAPS = 3;
    public static final int HEX_STYLE_0X = 4;
    public static final int HEX_STYLE_0X_CAPS = 5;
    
    public static final String CPC_TAG = "cpc";
    public static final String SDCC_UNSAFE_TAG = "sdcc-unsafe";
    public static final String TSTATEZ80_TAG = "tstatez80";

    public static final int CODE_FROM_INPUT_FILE = 0;
    public static final int CODE_FROM_SEARCHBASEDOPTIMIZER = 1;
    public static final int CODE_FROM_DISASSEMBLY = 2;
    
    public static final int FILE_SEARCH_RELATIVE_TO_INCLUDING_FILE = 0;
    public static final int FILE_SEARCH_ADDITIONAL_PATHS = 1;
    public static final int FILE_SEARCH_ORIGINAL_FILE_PATH = 2;
    public static final int FILE_SEARCH_WORKING_DIRECTORY = 3;
    
    public int codeSource = CODE_FROM_INPUT_FILE;
    
    // arguments:
    public List<String> inputFiles = new ArrayList<>();
    public String symbolTableOutputFile = null;
    public String symbolTableAllOutputFile = null;
    public String sourceFileOutputFile = null;
    public String dotOutputFile = null;

    public String cpu = "z80msx";
    public String cpuInstructionSet = "data/z80msx-instruction-set.tsv";
    public String timeUnit = "t-state";
    public boolean hexStyleChanged = false; // "true" if the user explicitly requests a hex style
    public int hexStyle = HEX_STYLE_HASH;
    public String dialect = Dialects.defaultDialect();
    public Dialect dialectParser = null;
    public List<File> includeDirectories = new ArrayList<>();

    public boolean eagerMacroEvaluation = true;
    // in some dialects, instead of ALL macros being evaluated eagerly, only a 
    // few are (like conditionals):
    public List<String> macrosToEvaluateEagerly = new ArrayList<>();   
    public boolean includeBinariesInAnalysis = false;
    public boolean labelsHaveSafeValues = true;  // If this is false, the optimizers will not trust
                                                 // the value MDL calculates for labels. This is useful, for example,
                                                 // for the SDCC dialect, where we don't know the base address ahead
                                                 // of time.
    public boolean considerLinesEndingInCommaAsUnfinished = false;
    public boolean relativizeIncbinPaths = true;
    public boolean useSingleQotesForsingleCharStrings = false;
    public boolean fix_tniasm_parenthesisExpressionBug = false;  // tniasm has a curious bug, when a line looks like this:
                                                                 //   ld hl, (1+2) + 3
                                                                 // since the expression starts with a "(", it believes it
                                                                 // is an indirection and cannot parse the expression (due
                                                                 // to the extra "+ 3". To fix it, when an expression
                                                                 // happens to start with a parenthesis, but it's not an 
                                                                 // indirection, we just do: 0 + (<exp>), which fixes the issue.
    
    public List<String> ignorePatternsWithTags = new ArrayList<>();
    
    // warning messages:
    public boolean warning_labelWithoutColon = false;
    public boolean warning_jpHlWithParenthesis = false;
    public boolean warning_unofficialOps = false;
    public boolean warning_ambiguous = false;
    public boolean warning_labelless_jump = false;

    public boolean convertToOfficial = true;
    public boolean evaluateAllExpressions = false;
    public boolean evaluateDialectFunctions = true;
    public boolean caseSensitiveSymbols = true;
    public boolean convertSymbolstoUpperCase = false;
    public boolean safetyLabelsForJumpsToConstants = false;
    public boolean considerCpuOpSymbolsWithoutIndentationToBeLabels = false;
    public boolean tryParsingUndefinedSymbolsAsHex = false;

    // Two variables, as if they are both false, no conversion is done
    public boolean output_opsInLowerCase = true;
    public boolean output_opsInUpperCase = false;
    
    public boolean output_allowDSVirtual = false;
    public boolean output_equsWithoutColon = false;
    public boolean output_safetyEquDollar = true;
    public boolean output_replaceLabelDotsByUnderscores = false;
    public boolean output_indirectionsWithSquareBrakets = false;
    public boolean output_replaceDsByData = false;
    
    public boolean help_triggered = false;
    public boolean display_simple_help = false;

    // Symbols defined via flags:
    public List<String> symbolDefinitions = new ArrayList<>();
    
    // code annotations:
    public String PRAGMA_NO_OPTIMIZATION = "mdl:no-opt";
    public String PRAGMA_NO_OPTIMIZATION_START = "mdl:no-opt-start";
    public String PRAGMA_NO_OPTIMIZATION_END = "mdl:no-opt-end";
    
    // Path search order (which is different in different dialects):
    public int filePathSearchOrder[] = {FILE_SEARCH_RELATIVE_TO_INCLUDING_FILE,
                                        FILE_SEARCH_ADDITIONAL_PATHS,
                                        FILE_SEARCH_WORKING_DIRECTORY
    };

    // If this is != null, includes with "<...>" syntax are allowed, and will
    // use this search path order:
    public int bracketIncludeFilePathSearchOrder[] = null;
    
    // Dialect quirks:
    public boolean quirk_sjasm_struc = false;
    public boolean quirk_sjasmplus_dirbol_double_directive = false;
    
    // utils:
    public MDLLogger logger;
    public Tokenizer tokenizer;
    public PreProcessor preProcessor;
    public LineParser lineParser;
    public ExpressionParser expressionParser;
    public CodeBaseParser codeBaseParser;
    public CPUOpSpecParser opSpecParser;
    public CPUOpParser opParser;
    
    List<MDLWorker> workers = new ArrayList<>();
    
    // Accumulated stats to be reported at the end of execution:
    public OptimizationResult optimizerStats = new OptimizationResult();

    // This string has MD tags, so that I can easily generate the corresponding documentation in github with the 
    // hidden "-helpmd" flag:
    public String docString = "MDL "+Main.VERSION_STRING+" (A Z80 assembler optimizer) by Santiago Ontañón (Brain Games, 2020-2022)\n"
            + "https://github.com/santiontanon/mdlz80optimizer\n"
            + "\n"
            + "Command Line Arguments:\n"
            + "```java -jar mdl.jar <input file name(s)> [options]```\n"
            + "\n"
            + "Several input file names can be specified, separated by spaces. In case that more than one input file name is specified, MDL will just act as if there was a master assembler file that includes them all in the specified order.\n"
            + "Note: all the tasks concerning generating outputs (assembler, binaries, etc.) will be executed after the optimizers are run.\n"
            + "\n"
            + "- ```-help```: to show this information (this is the only flag that can be used without specifying an input file).\n"
            + "- ```-cpu <type>```: to select a different CPU (z80/z80msx/z80cpc/z80n/z80next/z180), where z80n and z80next are synonyms (default: z80msx).\n"
            + "- ```-dialect <dialect>```: to allow parsing different assembler dialects "
                    + "(" + StringUtils.join(Dialects.knownDialects(), '/') + ") "
                    + "(default: mdl, which supports some basic code idioms common to various assemblers).\n"
            + "                   Note that even when selecting a dialect, not all syntax of a given assembler might be supported.\n"
            + "- ```-I <folder>```: adds a folder to the include search path (```-inc <folder>``` can also be used for compatibility with other assemblers).\n"
            + "- ```-equ <symbol>=<value>```: defines a symbol that will exist while parsing the assembler code.\n"
            + "- ```-ansion```: turns on color message output usin ANSI codes (default: on in Unix, off in Windows).\n"
            + "- ```-ansioff```: turns off color message output usin ANSI codes.\n"
            + "- ```-quiet```: turns off info messages; only outputs warnings and errors.\n"
            + "- ```-diggest```: turns off most info messages; only outputs summary messages, warnings and errors.\n"
            + "- ```-debug```: turns on debug messages.\n"
            + "- ```-trace```: turns on trace messages.\n"
            + "- ```-warn```: turns on all warnings.\n"
            + "- ```-warn-labelnocolon```: turns on warnings for not placing colons after labels.\n"
            + "- ```-warn-jp(rr)```: turns on warnings for using confusing 'jp (hl)' instead of 'jp hl' (this is turned off by default in dialects that do not support this).\n"
            + "- ```-warn-unofficial```: turns on warnings for using unofficial op syntax (e.g., 'add 1' instead of 'add a,1'.\n"
            + "- ```-warn-ambiguous```: turns on warnings for using ambiguous or error-inducing syntax in some dialects.\n"
            + "- ```-warn-labelless-jump```: turns on warnings for using jumps that instead of jumping to a label, are defined as something like 'jp $+5'."
            + "- ```-do-not-convert-to-official```: turns off automatic conversion of unofficial op syntax to official ones in assembler output.\n"
            + "- ```-hex#```: hex numbers render like #ffff (default). These flags also have analogous effect on binary and octal constant rendering.\n"
            + "- ```-HEX#```: hex numbers render like #FFFF.\n"
            + "- ```-hexh```: hex numbers render like 0ffffh.\n"
            + "- ```-HEXH```: hex numbers render like 0FFFFh.\n"
            + "- ```-hex0x```: hex numbers render like 0xffff.\n"
            + "- ```-HEX0X```: hex numbers render like 0XFFFF.\n"
            + "- ```-+bin```: includes binary files (incbin) in the output analyses.\n"
            + "- ```-no-opt-pragma <value>```: changes the pragma to be inserted in a comment on a line to prevent optimizing it (default: "
            + PRAGMA_NO_OPTIMIZATION + ")\n"
            + "- ```-no-opt-start-pragma <value>```: changes the pragma to be inserted in a comment on a line to mark it as the start of a block of lines to be protected from optimization (default: "
            + PRAGMA_NO_OPTIMIZATION_START + ")\n"
            + "- ```-no-opt-end-pragma <value>```: changes the pragma to be inserted in a comment on a line to mark it as the end of a block of lines to be protected from optimization (default: "
            + PRAGMA_NO_OPTIMIZATION_END + ")\n"
            + "- ```-out-opcase <case>```: whether to convert the assembler operators to upper or lower case. Possible values are: none/lower/upper (none does no conversion). Default is 'lower'.\n"
            + "- ```-out-allow-ds-virtual```: allows 'ds virtual' in the generated assembler (not all assemblers support this, but simplifies output)\n"
            + "- ```-out-colonless-equs```: equs will look like 'label equ value' instead of 'label: equ value'\n"
            + "- ```-out-remove-safety-equdollar```: labels preceding an equ statement are rendered as 'label: equ $' by default for safety (some assemblers interpret them differently otherwise). Use this flag to deactivate this behavior.\n"
            + "- ```-out-labels-no-dots```: local labels get resolved to `context.label', some assemblers do not like '.' in labels however. This flag replaces them by underscores.\n"
            + "- ```-out-squarebracket-ind```: use [] for indirections in the output, rather than ().\n"
            + "- ```-out-data-instead-of-ds```: will replace statements like ```ds 4, 0``` by ```db 0, 0, 0, 0```.\n"
            + "- ```-out-do-not-evaluate-dialect-functions```: some assembler dialects define functions like random/sin/cos that can be used to form expressions. By default, MDL replaces them by the result of their execution before generating assembler output (as those might not be defined in other assemblers, and thus this keeps the assembler output as compatible as possible). Use this flag if you don't want this to happen.\n"
            + "- ```-out-evaluate-all-expressions```: this flag makes MDL resolve all expressions down to their ultimate numeric or string value when generating assembler code.\n"
            + "- ```-safety-labels-for-jumps-to-constants```: makes MDL replace the destination of a jump/call to a constant (e.g. ```jp #3c4a```) by a label. MDL does not do this by default since calls to constants are often used for BIOS calls (although replacing those constants by labels is recommended). Jumpts to constants are unsafe for optimization as the code at the target address (```#3c4a``` in the example) might move as a result of optimization. Hence, it's safer to add a safety label at the target address and use it for the jump.\n"
            + "- ```-quirk-sjasm-struc```: allows having the keyword ```struc``` after the definition of a struct in sjasm (as in ```STRUCT mystruct struc```), since sjasm allows this (probably by accident), and some codebases have it.\n"
            + "- ```-quirk-sjasmplus-dirbol-double-directive```: allows two directives in the same line without any separator, like this: ```db 0,0,0 dw 0``` (this is not intended, and will be fixed in future versions of sjasmplus, but some codebases use it).\n";

    public String simpleDocString = "MDL "+Main.VERSION_STRING+" (A Z80 assembler optimizer) by Santiago Ontañón (Brain Games, 2020-2022)\n"
            + "https://github.com/santiontanon/mdlz80optimizer\n"
            + "\n"
            + "Command Line Arguments:\n"
            + "```java -jar mdl.jar <input file name(s)> [options]```\n"
            + "\n"
            + "- ```-help```: for an exhaustive list of flags (just type ```java -jar mdl.jar -help```).\n"
            + "- ```-dialect <dialect>```: selects which assembler dialect to use "
                    + "(" + StringUtils.join(Dialects.knownDialects(), '/') + ").\n";
    
    
    public MDLConfig() {
        logger = new MDLLogger(MDLLogger.INFO);
        // Detect if we are on a unix system and activate color ANSI codes (which
        // are not supported by default on Windows):
        if (System.console() != null && System.getenv().get("TERM") != null) {
            // use colors by default only on Unix (otherwi
            logger.useColors(true);
        } else {
            logger.useColors(false);
        }
        
        // ignore all the platform specific patterns, by default:
        ignorePatternsWithTags.add(CPC_TAG);

        tokenizer = new Tokenizer();          
    }


    public void registerWorker(MDLWorker r) {
        workers.add(r);
        docString += r.docString();
        simpleDocString += r.simpleDocString();
    }

    public boolean executeWorkers(CodeBase code) {
        for (MDLWorker w : workers) {
            if (w.triggered()) {
                if (!w.work(code)) {
                    error("Problem executing worker " + w.getClass().getSimpleName());
                    return false;
                }
            }
        }
        return true;
    }


    public boolean somethingToDo() {
        for (MDLWorker w : workers) {
            if (w.triggered()) {
                return true;
            }
        }
        return false;
    }
    
    
    /*
     * Returns null if everything is fine, and an error string otherwise.
     */
    public boolean parseArgs(String... argsArray) throws IOException {
        List<String> args = new ArrayList<>();
        for(String arg:argsArray) args.add(arg);

        while (!args.isEmpty()) {
            String arg = args.get(0);
            if (arg.startsWith("-")) {
                switch (arg) {
                    case "-cpu":
                        if (args.size()>=2) {
                            args.remove(0);
                            cpu = args.remove(0);
                            switch(cpu) {
                                case "z80":
                                    cpuInstructionSet = "data/z80-instruction-set.tsv";
                                    timeUnit = "t-state";
                                    break;
                                case "z80msx":
                                    cpuInstructionSet = "data/z80msx-instruction-set.tsv";
                                    timeUnit = "t-state";
                                    break;
                                case "z80cpc":
                                    cpuInstructionSet = "data/z80cpc-instruction-set.tsv";
                                    timeUnit = "nop";
                                    ignorePatternsWithTags.remove(CPC_TAG);
                                    ignorePatternsWithTags.add(TSTATEZ80_TAG);
                                    break;
                                case "z80n":
                                case "z80next":
                                    cpuInstructionSet = "data/z80n-instruction-set.tsv";
                                    timeUnit = "t-state";
                                    break;
                                case "z180":
                                    cpuInstructionSet = "data/z180-instruction-set.tsv";
                                    timeUnit = "t-state";
                                    break;
                                default:
                                error("Unrecognized cpu " + cpu);
                                    return false;
                            }
                        } else {
                            error("Missing cpu name after " + arg);
                            return false;
                        }
                        break;

                    case "-dialect":
                        if (args.size() < 2) {
                            error("Missing dialect name after " + arg);
                            return false;
                        }
                        args.remove(0);
                        String dialectString = args.remove(0);
                        if (!Dialects.selectDialect(dialectString, this)) {
                            error("Unrecognized dialect " + dialectString);
                            return false;
                        }
                        break;

                    case "-I":
                    case "-inc":
                        if (args.size()>=2) {
                            args.remove(0);
                            final File includePath = new File(args.remove(0));
                            if ((includePath.isDirectory())) {
                                includeDirectories.add(includePath);
                            } else {
                                warn("Include path "+includePath+" is not a directory and will be ignored");
                            }
                        } else {
                            error("Missing path after " + arg);
                            return false;
                        }
                        break;
                        
                    case "-equ":
                        if (args.size()>=2) {
                            args.remove(0);
                            symbolDefinitions.add(args.remove(0));
                        }
                        break;

                    case "-ansion":
                        logger.useColors(true);
                        args.remove(0);
                        break;

                    case "-ansioff":
                        logger.useColors(false);
                        args.remove(0);
                        break;
                        
                    case "-quiet":
                        logger.minLevelToLog = MDLLogger.WARNING;
                        args.remove(0);
                        break;

                    case "-diggest":
                        logger.minLevelToLog = MDLLogger.DIGGEST;
                        args.remove(0);
                        break;

                    case "-debug":
                        logger.minLevelToLog = MDLLogger.DEBUG;
                        args.remove(0);
                        break;

                    case "-trace":
                        logger.minLevelToLog = MDLLogger.TRACE;
                        args.remove(0);
                        break;

                    case "-warn":
                        warning_labelWithoutColon = true;
                        warning_jpHlWithParenthesis = true;
                        warning_unofficialOps = true;
                        warning_ambiguous = true;
                        args.remove(0);
                        break;

                    case "-warn-labelnocolon":
                        warning_labelWithoutColon = true;
                        args.remove(0);
                        break;

                    case "-warn-jp(rr)":
                        warning_jpHlWithParenthesis = true;
                        args.remove(0);
                        break;

                    case "-warn-unofficial":
                        warning_unofficialOps = true;
                        args.remove(0);
                        break;

                    case "-warn-ambiguous":
                        warning_ambiguous = true;
                        args.remove(0);
                        break;
                        
                    case "-do-not-convert-to-official":
                        convertToOfficial = false;
                        args.remove(0);
                        break;

                    case "-+bin":
                        includeBinariesInAnalysis = true;
                        args.remove(0);
                        break;

                    case "-hex#":
                        hexStyle = HEX_STYLE_HASH;
                        hexStyleChanged = true;
                        args.remove(0);
                        break;

                    case "-HEX#":
                        hexStyle = HEX_STYLE_HASH_CAPS;
                        hexStyleChanged = true;
                        args.remove(0);
                        break;

                    case "-hexh":
                        hexStyle = HEX_STYLE_H;
                        hexStyleChanged = true;
                        args.remove(0);
                        break;

                    case "-HEXH":
                        hexStyle = HEX_STYLE_H_CAPS;
                        hexStyleChanged = true;
                        args.remove(0);
                        break;

                    case "-hex0x":
                        hexStyle = HEX_STYLE_0X;
                        hexStyleChanged = true;
                        args.remove(0);
                        break;

                    case "-HEX0X":
                        hexStyle = HEX_STYLE_0X_CAPS;
                        hexStyleChanged = true;
                        args.remove(0);
                        break;
                        
                    case "-no-opt-pragma":
                        if (args.size()>=2) {
                            args.remove(0);
                            PRAGMA_NO_OPTIMIZATION = args.remove(0);
                        } else {
                            error("Missing pragma after " + arg);
                            return false;
                        }
                        break;

                    case "-out-opcase":
                        if (args.size()>=2) {
                            args.remove(0);
                            switch(args.remove(0)) {
                                case "none":
                                    output_opsInLowerCase = false;
                                    output_opsInUpperCase = false;
                                    break;
                                case "lower":
                                    output_opsInLowerCase = false;
                                    output_opsInUpperCase = true;
                                    break;
                                case "upper":
                                    output_opsInLowerCase = true;
                                    output_opsInUpperCase = false;
                                    break;
                                default:
                                    error("Unknown value for -opcase argument!");
                                    return false;
                            }

                        } else {
                            error("Missing pragma after " + arg);
                            return false;
                        }
                        break;
                        
                    case "-out-allow-ds-virtual":
                        output_allowDSVirtual = true;
                        args.remove(0);
                        break;

                    case "-out-colonless-equs":
                        output_equsWithoutColon = true;
                        args.remove(0);
                        break;

                    case "-out-remove-safety-equdollar":
                        output_safetyEquDollar = false;
                        args.remove(0);
                        break;
                        
                    case "-out-labels-no-dots":
                        output_replaceLabelDotsByUnderscores = true;
                        args.remove(0);
                        break;
                        
                    case "-out-squarebracket-ind":
                        output_indirectionsWithSquareBrakets = true;
                        args.remove(0);
                        break;
                                                
                    case "-out-do-not-evaluate-dialect-functions":
                        evaluateDialectFunctions = false;
                        args.remove(0);
                        break;

                    case "-out-data-instead-of-ds":
                        output_replaceDsByData = true;
                        args.remove(0);
                        break;
                        
                    case "-out-evaluate-all-expressions":
                        evaluateAllExpressions = true;
                        args.remove(0);
                        break;
                        
                    case "-safety-labels-for-jumps-to-constants":
                        safetyLabelsForJumpsToConstants = true;
                        args.remove(0);
                        break;
                        
                    case "-quirk-sjasm-struc":
                        quirk_sjasm_struc = true;
                        args.remove(0);
                        break;

                    case "-quirk-sjasmplus-dirbol-double-directive":
                        quirk_sjasmplus_dirbol_double_directive = true;
                        args.remove(0);
                        break;
                        
                    default:
                    {
                        MDLWorker recognizedBy = null;
                        for(MDLWorker w:workers) {
                            if (w.parseFlag(args)) {
                                recognizedBy = w;
                                break;
                            }
                        }
                        if (recognizedBy == null) {
                            error("Unrecognized argument " + arg);
                            return false;
                        }
                    }
                }
            } else {
                inputFiles.add(args.remove(0));
            }
        }

        opSpecParser = new CPUOpSpecParser(this);
        preProcessor = new PreProcessor(this);
        codeBaseParser = new CodeBaseParser(this);
        lineParser = new LineParser(this, codeBaseParser);
        expressionParser = new ExpressionParser(this);
        opParser = new CPUOpParser(opSpecParser.parseSpecs(), this);
        dialectParser = Dialects.getDialectParser(dialect, this);
                
        if (!somethingToDo()) {
            help_triggered = true;
            display_simple_help = true;
        }
                
        return verify();
    }


    /*
        Returns null if everything is fine, and an error string otherwise.
     */
    public boolean verify() {
        if (inputFiles.isEmpty() && !help_triggered) {
            error("Missing inputFile");
            return false;
        }
        return true;
    }


    public void trace(String message) {
        logger.log(MDLLogger.TRACE, message);
    }


    public void debug(String message) {
        logger.log(MDLLogger.DEBUG, message);
    }


    public void info(String message) {
        logger.log(MDLLogger.INFO, message);
    }


    public void diggest(String message) {
        logger.log(MDLLogger.DIGGEST, message);
    }


    public void warn(String message) {
        logger.log(MDLLogger.WARNING, message);
    }


    public void error(String message) {
        logger.log(MDLLogger.ERROR, message);
    }


    // Logging with pre-defined format, to produce messages easy to parse by text editors:
    public void info(String tag, String fileNameLine, String message) {
        logger.log(MDLLogger.INFO, tag + " in " + fileNameLine + ": " + message);
    }

    
    public void warn(String tag, String fileNameLine, String message) {
        logger.log(MDLLogger.WARNING, tag + " in " + fileNameLine + ": " + message);
    }
    

    public boolean isInfoEnabled()
    {
        return logger.minLevelToLog <= MDLLogger.INFO;
    }


    public boolean isDebugEnabled()
    {
        return logger.minLevelToLog <= MDLLogger.DEBUG;
    }
}
