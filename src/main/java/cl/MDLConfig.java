/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package cl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import ch.qos.logback.classic.Level;
import code.CodeBase;
import parser.CPUOpParser;
import parser.CPUOpSpecParser;
import parser.CodeBaseParser;
import parser.ExpressionParser;
import parser.LineParser;
import parser.PreProcessor;
import parser.dialects.ASMSXDialect;
import parser.dialects.Dialect;
import parser.dialects.GlassDialect;
import parser.dialects.SjasmDialect;
import workers.MDLWorker;

public class MDLConfig {
    // constants:
    public static final int HEX_STYLE_HASH = 0;
    public static final int HEX_STYLE_HASH_CAPS = 1;
    public static final int HEX_STYLE_H = 2;
    public static final int HEX_STYLE_H_CAPS = 3;

    public static final int CPU_Z80 = 0;
    public static final int CPU_Z80MSX = 1;
    public static final int CPU_Z80CPC = 2;

    public static final int DIALECT_MDL = 0;
    public static final int DIALECT_GLASS = 1;
    public static final int DIALECT_ASMSX = 2;
    public static final int DIALECT_SJASM = 3;

    // arguments:
    public String inputFile = null;
    public String symbolTableOutputFile = null;
    public String symbolTableAllOutputFile = null;
    public String sourceFileOutputFile = null;
    public String dotOutputFile = null;

    public int cpu = CPU_Z80MSX;
    public int hexStyle = HEX_STYLE_HASH;
    public int dialect = DIALECT_MDL;
    public Dialect dialectParser = null;
    public List<File> includeDirectories = new ArrayList<>();

    public boolean includeBinariesInAnalysis = false;

    public boolean warningLabelWithoutColon = true;
    public boolean warningJpHlWithParenthesis = true;

    // code annotations:
    public String PRAGMA_NO_OPTIMIZATION = "mdl:no-opt";

    // utils:
    public PreProcessor preProcessor;
    public LineParser lineParser;
    public ExpressionParser expressionParser;
    public CodeBaseParser codeBaseParser;
    public CPUOpParser opParser;

    List<MDLWorker> workers = new ArrayList<>();

    public String docString = "MDL (A Z80 assembler optimizer) by Santiago Ontañón (Brain Games, 2020)\n"
            + "https://github.com/santiontanon/mdlz80optimizer\n" + "\n"
            + "arguments: <input assembler file> [options]\n"
            + "  -cpu <type>: to select a different CPU (z80/z80msx/z80cpc) (default: z80msx).\n"
            + "  -dialect <type>: to allow parsing different assembler dialects (mdl/glass/asmsx/sjasm) (default: mdl, which supports some basic code idioms common to various assemblers).\n"
            + "                   Note that even when selecting a dialect, not all syntax of a given assembler might be supported.\n"
            + "  -I <folder>: adds a folder to the include search path.\n"
            + "  -quiet: turns off info messages; only outputs warnings and errors.\n"
            + "  -debug: turns on debug messages.\n"
            + "  -trace: turns on trace messages.\n"
            + "  -warn-off-labelnocolon: turns off warnings for not placing colons after labels.\n"
            + "  -warn-off-jp(rr): turns off warnings for using confusing 'jp (hl)' instead of 'jp hl' (this is turned off by default in dialects that do not support this).\n"
            + "  -hex#: hex numbers render like #ffff (default).\n" + "  -HEX#: hex numbers render like #FFFF.\n"
            + "  -hexh: hex numbers render like 0ffffh.\n" + "  -HEXH: hex numbers render like 0FFFFh.\n"
            + "  -+bin: includes binary files (incbin) in the output analyses.\n"
            + "  -no-opt-pragma <value>: changes the pragma to be inserted in a comment on a line to prevent optimizing it (default: "
            + PRAGMA_NO_OPTIMIZATION + ")" + "\n";

    public void registerWorker(MDLWorker r) {
        workers.add(r);
        docString += r.docString();
    }

    public void executeWorkers(CodeBase code) {
        for (MDLWorker w : workers) {
            if (!w.work(code)) {
                MDLLogger.logger().error("Problem executing worker {}", w.getClass().getSimpleName());
            }
        }
    }

    /*
     * Returns null if everything is fine, and an error string otherwise.
     */
    public boolean parse(String... argsArray) throws IOException {
        if (argsArray.length == 0) {
            MDLLogger.logger().info(docString);
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
                                MDLLogger.logger().error("Unrecognized cpu {}", cpuString);
                                    return false;
                            }
                        } else {
                            MDLLogger.logger().error("Missing cpu name after {}", arg);
                            return false;
                        }
                        break;

                    case "-dialect":
                        if (args.size()>=2) {
                            args.remove(0);
                            String dialectString = args.remove(0);
                            switch(dialectString) {
                                case "mdl":
                                    dialect = DIALECT_MDL;
                                    dialectParser = null;
                                    break;
                                case "glass":
                                    dialect = DIALECT_GLASS;
                                    break;
                                case "asmsx":
                                    dialect = DIALECT_ASMSX;
                                    break;
                                case "sjasm":
                                    dialect = DIALECT_SJASM;
                                    break;
                                default:
                                    MDLLogger.logger().error("Unrecognized dialect {}", dialectString);
                                    return false;
                            }
                        } else {
                            MDLLogger.logger().error("Missing dialect name after {}", arg);
                            return false;
                        }
                        break;

                    case "-I":
                        if (args.size()>=2) {
                            args.remove(0);
                            final File includePath = new File(args.remove(0));
                            if ((includePath.isDirectory())) {
                                includeDirectories.add(includePath);
                            } else {
                                MDLLogger.logger().warn("Include path {} is not a directory and will be ignored", includePath);
                            }
                        } else {
                            MDLLogger.logger().error("Missing path after {}", arg);
                            return false;
                        }
                        break;

                    case "-quiet":
                        MDLLogger.INSTANCE.setMinLevelToLog(Level.WARN);
                        args.remove(0);
                        break;

                    case "-debug":
                        MDLLogger.INSTANCE.setMinLevelToLog(Level.DEBUG);
                        args.remove(0);
                        break;

                    case "-trace":
                        MDLLogger.INSTANCE.setMinLevelToLog(Level.TRACE);
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

                    case "-no-opt-pragma":
                        if (args.size()>=2) {
                            args.remove(0);
                            PRAGMA_NO_OPTIMIZATION = args.remove(0);
                        } else {
                            MDLLogger.logger().error("Missing pragma after {}", arg);
                            return false;
                        }
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
                            MDLLogger.logger().error("Unrecognized argument {}", arg);
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
                        MDLLogger.logger().error("Unrecognized argument {}", arg);
                        return false;
                }
            }
        }


        CPUOpSpecParser opSpecParser = new CPUOpSpecParser(this);

        preProcessor = new PreProcessor(this);
        codeBaseParser = new CodeBaseParser(this);
        lineParser = new LineParser(this, codeBaseParser);
        expressionParser = new ExpressionParser(this);
        opParser = new CPUOpParser(opSpecParser.parseSpecs(), this);

        switch(dialect) {
            case DIALECT_GLASS:
                dialectParser = new GlassDialect(this);
                break;
            case DIALECT_ASMSX:
                dialectParser = new ASMSXDialect(this);
                break;
            case DIALECT_SJASM:
                dialectParser = new SjasmDialect(this);
                break;

        }

        return verify();
    }


    /*
        Returns null if everything is fine, and an error string otherwise.
     */
    public boolean verify() {
        if (inputFile == null) {
            MDLLogger.logger().error("Missing inputFile");
            return false;
        }
        return true;
    }

    public void annotation(String fileName, int lineNumber, String tag, String message) {
        MDLLogger.INSTANCE.annotation(fileName, lineNumber, tag, message);
    }

}
