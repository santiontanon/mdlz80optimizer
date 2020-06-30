/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package cl;

import code.CodeBase;
import java.util.ArrayList;
import java.util.List;
import parser.CPUOpParser;
import parser.CPUOpSpecParser;
import workers.MDLWorker;
import parser.CodeBaseParser;
import parser.ExpressionParser;
import parser.LineParser;
import parser.idioms.Glass;
import parser.idioms.Idiom;

public class MDLConfig {

    // constants:
    public static int HEX_STYLE_HASH = 0;
    public static int HEX_STYLE_HASH_CAPS = 1;
    public static int HEX_STYLE_H = 2;
    public static int HEX_STYLE_H_CAPS = 3;
    
    public static int CPU_Z80 = 0;
    public static int CPU_Z80MSX = 1;
    public static int CPU_Z80CPC = 2;
    
    public static int IDIOM_MDL = 0;
    public static int IDIOM_GLASS = 1;

    // arguments:
    public String inputFile = null;
    public String symbolTableOutputFile = null;
    public String symbolTableAllOutputFile = null;
    public String sourceFileOutputFile = null;
    public String dotOutputFile = null;

    public int cpu = CPU_Z80MSX;
    public int hexStyle = HEX_STYLE_HASH;
    public int idiom = IDIOM_MDL;
    public Idiom idiomParser = null;
    public List<String> includeDirectories = new ArrayList<>();
    
    public boolean includeBinariesInAnalysis = false;

    public boolean warningLabelWithoutColon = true;
    public boolean warningJpHlWithParenthesis = true;

    // utils:
    public MDLLogger logger;
    public LineParser lineParser;
    public ExpressionParser expressionParser;
    public CodeBaseParser codeBaseParser;
    public CPUOpParser opParser;
    
    List<MDLWorker> workers = new ArrayList<>();    
    

    public static String docString
            = "MDL (A Z80 assembler optimizer) by Santiago Ontañón (Brain Games, 2020)\n"
            + "https://github.com/santiontanon/mdlz80optimizer\n"
            + "\n"
            + "arguments: <input assembler file> [options]\n"
            + "  -cpu <type>: to select a different CPU (z80/z80msx/z80cpc) (default: z80msx).\n"
            + "  -idiom <type>: to allow parsing different assembler idioms (mdl/glass) (default: mdl, which supports some basic code idioms common to various assemblers).\n"
            + "                 Note that even when selecting an idiom, not all syntax of a given assembler might be supported.\n"
            + "  -I <folder>: adds a folder to the include search path.\n"
            + "  -debug: turns on debug messages.\n"
            + "  -warn-off-labelnocolon: turns off warnings for not placing colons after labels.\n"
            + "  -warn-off-jp(rr): turns off warnings for using confusing 'jp (hl)' instead of 'jp hl'.\n"
            + "  -hex#: hex numbers render like #ffff (default).\n"
            + "  -HEX#: hex numbers render like  #FFFF.\n"
            + "  -hexh: hex numbers render like  0ffffh.\n"
            + "  -HEXH: hex numbers render like  0FFFFh.\n"
            + "  -+bin: includes binary files (incbin) in the output analyses.\n"
            + "\n";

    
    public MDLConfig() {
        logger = new MDLLogger(MDLLogger.INFO);
    }

    
    public void registerWorker(MDLWorker r)
    {
        workers.add(r);
        docString += r.docString();
    }
    
    
    public void executeWorkers(CodeBase code) throws Exception
    {
        for(MDLWorker w:workers) {
            if (!w.work(code)) {
                error("Problem executing worker " + w.getClass().getSimpleName());
            }
        }
    }
    
    
    /*
        Returns null if everything is fine, and an error string otherwise.
     */
    public boolean parse(String argsArray[]) throws Exception {
        if (argsArray.length == 0) {
            info(docString);
            return false;
        }
        
        List<String> args = new ArrayList<>();
        for(String arg:argsArray) args.add(arg);

        int state = 0;
        while (!args.isEmpty()) {
            String arg = args.get(0);
            if (arg.startsWith("-")) {
                switch (arg) {
                    case "-cpu":
                        if (args.size()>=2) {
                            args.remove(0);
                            String cpuString = args.remove(0);
                            switch(cpuString) {
                                case "z80":
                                    cpu = CPU_Z80;
                                    break;
                                case "z80msx":
                                    cpu = CPU_Z80MSX;
                                    break;
                                case "z80cpc":
                                    cpu = CPU_Z80CPC;
                                    break;
                                default:
                                    error("Unrecognized cpu " + cpuString);
                                    return false;
                            }
                        } else {
                            error("Missing cpu name after " + arg);
                            return false;
                        }
                        break;

                    case "-idiom":
                        if (args.size()>=2) {
                            args.remove(0);
                            String idiomString = args.remove(0);
                            switch(idiomString) {
                                case "mdl":
                                    idiom = IDIOM_MDL;
                                    idiomParser = null;
                                    break;
                                case "glass":
                                    idiom = IDIOM_GLASS;
                                    idiomParser = new Glass(this);
                                    break;
                                default:
                                    error("Unrecognized idiom " + idiomString);
                                    return false;
                            }
                        } else {
                            error("Missing idiom name after " + arg);
                            return false;
                        }
                        break;
                        
                    case "-I":
                        if (args.size()>=2) {
                            args.remove(0);                        
                            includeDirectories.add(args.remove(0));
                        } else {
                            error("Missing path after " + arg);
                            return false;
                        }
                        break;
    
                    case "-debug":
                        logger.setMinLevelToLog(MDLLogger.DEBUG);
                        args.remove(0);
                        break;
                        
                    case "-warn-off-labelnocolon":
                        warningLabelWithoutColon = false;
                        args.remove(0);
                        break;

                    case "-warn-off-jp(rr)":
                        warningJpHlWithParenthesis = false;
                        args.remove(0);
                        break;

                    case "-+bin":
                        includeBinariesInAnalysis = true;
                        args.remove(0);
                        break;

                    case "-hex#":
                        hexStyle = HEX_STYLE_HASH;
                        args.remove(0);
                        break;

                    case "-HEX#":
                        hexStyle = HEX_STYLE_HASH_CAPS;
                        args.remove(0);
                        break;

                    case "-hexh":
                        hexStyle = HEX_STYLE_H;
                        args.remove(0);
                        break;

                    case "-HEXH":
                        hexStyle = HEX_STYLE_H_CAPS;
                        args.remove(0);
                        break;
                        

                    default:
                    {
                        boolean recognized = false;
                        for(MDLWorker w:workers) {
                            if (w.parseFlag(args)) {
                                recognized = true;
                                break;
                            }
                        }
                        if (!recognized) {
                            error("Unrecognized argument " + arg);
                            return false;
                        }
                    }
                }
            } else {
                switch (state) {
                    case 0:
                        inputFile = args.remove(0);
                        state++;
                        break;
                    default:
                        error("Unrecognized argument " + arg);
                        return false;
                }
            }
        }
        
        
        CPUOpSpecParser opSpecParser = new CPUOpSpecParser(this);

        codeBaseParser = new CodeBaseParser(this);
        lineParser = new LineParser(this, codeBaseParser);
        expressionParser = new ExpressionParser(this);
        opParser = new CPUOpParser(opSpecParser.parseSpecs(), this);
                
        return verify();
    }
        

    /*
        Returns null if everything is fine, and an error string otherwise.
     */
    public boolean verify() {
        if (inputFile == null) {
            error("Missing inputFile");
            return false;
        }
        return true;
    }
    
    public void debug(String message) {
        logger.log(MDLLogger.DEBUG, message);
    }

    public void info(String message) {
        logger.log(MDLLogger.INFO, message);
    }

    public void warn(String message) {
        logger.log(MDLLogger.WARNING, message);
    }

    public void error(String message) {
        logger.log(MDLLogger.ERROR, message);
    }

}
