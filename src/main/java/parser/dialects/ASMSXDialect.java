/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package parser.dialects;


import cl.MDLConfig;
import code.CPUOp;
import code.CodeBase;
import code.Expression;
import code.SourceConstant;
import code.SourceFile;
import code.SourceStatement;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import parser.SourceLine;
import parser.Tokenizer;
import util.Resources;

/**
 *
 * @author santi
 */
public class ASMSXDialect implements Dialect {
    public static final int ROM_STANDARD = -1;
    public static final int MEGAROM_KONAMI = 0;
    public static final int MEGAROM_KONAMI_SCC = 1;
    public static final int MEGAROM_ASCII8 = 2;
    public static final int MEGAROM_ASCII16 = 3;
    
    public static class PrintRecord {
        String keyword;
        SourceStatement previousStatement;  // not the current, as it was probably not added to the file
        Expression exp;
        
        public PrintRecord(String a_kw, SourceStatement a_prev, Expression a_exp)
        {
            keyword = a_kw;
            previousStatement = a_prev;
            exp = a_exp;
        }
    }
    
    MDLConfig config;

    Random r = new Random();

    String biosCallsFileName = "data/msx-bios-calls.asm";    
    String searchFileName = "data/asmsx-search.asm";    

    SourceStatement romHeaderStatement = null;
    SourceStatement basicHeaderStatement = null;
    Expression startAddressLabel = null;
    List<SourceStatement> pageDefinitions = new ArrayList<>();
    
    // ROM characteristics:
    int romType = ROM_STANDARD;
    int pageSize = 8*1024;
    int targetSizeInKB = 0;
    int currentPageEnd = 0;
    
    // Addresses are not resolved until the very end, so, when printing values, we just queue them up here, and
    // print them all at the very end:
    List<PrintRecord> toPrint = new ArrayList<>();
    

    public ASMSXDialect(MDLConfig a_config, boolean zilogMode)
    {
        config = a_config;

        config.lineParser.addKeywordSynonym(".org", config.lineParser.KEYWORD_ORG);
        config.lineParser.addKeywordSynonym(".include", config.lineParser.KEYWORD_INCLUDE);
        config.lineParser.addKeywordSynonym(".incbin", config.lineParser.KEYWORD_INCBIN);

        config.lineParser.addKeywordSynonym(".equ", config.lineParser.KEYWORD_EQU);

        config.lineParser.addKeywordSynonym(".db", config.lineParser.KEYWORD_DB);
        config.lineParser.addKeywordSynonym("defb", config.lineParser.KEYWORD_DB);
        config.lineParser.addKeywordSynonym(".defb", config.lineParser.KEYWORD_DB);
        config.lineParser.addKeywordSynonym("dt", config.lineParser.KEYWORD_DB);
        config.lineParser.addKeywordSynonym(".dt", config.lineParser.KEYWORD_DB);
        config.lineParser.addKeywordSynonym("deft", config.lineParser.KEYWORD_DB);
        config.lineParser.addKeywordSynonym(".deft", config.lineParser.KEYWORD_DB);

        config.lineParser.addKeywordSynonym(".dw", config.lineParser.KEYWORD_DW);
        config.lineParser.addKeywordSynonym("defw", config.lineParser.KEYWORD_DW);
        config.lineParser.addKeywordSynonym(".defw", config.lineParser.KEYWORD_DW);

        config.lineParser.addKeywordSynonym(".ds", config.lineParser.KEYWORD_DS);
        config.lineParser.addKeywordSynonym("defs", config.lineParser.KEYWORD_DS);
        config.lineParser.addKeywordSynonym(".defs", config.lineParser.KEYWORD_DS);

        
        config.lineParser.defineSpaceVirtualByDefault = true;

        config.warning_jpHlWithParenthesis = false;
        config.lineParser.keywordsHintingALabel.add("=");
        
        config.expressionParser.dialectFunctions.add(".random");
        config.expressionParser.dialectFunctions.add("random");
        config.expressionParser.dialectFunctions.add(".fix");
        config.expressionParser.dialectFunctions.add("fix");
        config.expressionParser.dialectFunctions.add(".sin");
        config.expressionParser.dialectFunctions.add("sin");
        config.expressionParser.dialectFunctions.add(".cos");
        config.expressionParser.dialectFunctions.add("cos");
        
        config.expressionParser.allowFloatingPointNumbers = true;
        
        if (zilogMode) {
            config.opParser.indirectionsOnlyWithSquareBrackets = false;
            config.opParser.indirectionsOnlyWithParenthesis = true;
        } else {
            config.opParser.indirectionsOnlyWithSquareBrackets = true;
            config.opParser.indirectionsOnlyWithParenthesis = false;
        }
    }


    @Override
    public boolean recognizeIdiom(List<String> tokens) {
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".bios")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("bios")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("filename")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".filename")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".size")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("size")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".page")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("page")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".printtext")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("printtext")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".print")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("print")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".printdec")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("printdec")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".printhex")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("printhex")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".byte")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("byte")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".word")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("word")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".rom")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("rom")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".basic")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("basic")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".start")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("start")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".search")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("search")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("=")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".wav")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("wav")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".cas")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("cas")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".megarom")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("megarom")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".select")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("select")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".zilog")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("zilog")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".subpage")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("subpage")) return true;
        
        // weird syntax that for some reason asMSX swallows (undocumented):
        // if a line is all dashes, it's ignored:
        {
            boolean allDashes = true;
            for(String token:tokens) {
                if (!token.equals("-") && !token.equals("--")) {
                    allDashes = false;
                    break;
                }
            }
            if (allDashes) return true;
        }
        return false;
    }

    
    private String getLastAbsoluteLabel(SourceStatement s) 
    {
        while(s != null) {
            if (s.label != null && s.label.isLabel() && 
                !s.label.originalName.startsWith(".") &&
                !s.label.originalName.startsWith("@@")) {
                return s.label.originalName;
            } else {
                s = s.source.getPreviousStatementTo(s, s.source.code);
            }
        }
        return null;        
    }
    

    @Override
    public String newSymbolName(String name, Expression value, SourceStatement s) {
        // TODO(santi@): complete this list (or maybe have a better way than an if-then rule list!
        if (name.equalsIgnoreCase(".bios") ||
            name.equalsIgnoreCase("bios") ||
            name.equalsIgnoreCase(".filename") ||
            name.equalsIgnoreCase("filename") ||
            name.equalsIgnoreCase(".size") ||
            name.equalsIgnoreCase("size") ||
            name.equalsIgnoreCase(".page") ||
            name.equalsIgnoreCase("page") ||
            name.equalsIgnoreCase(".printtext") ||
            name.equalsIgnoreCase("printtext") ||
            name.equalsIgnoreCase(".print") ||
            name.equalsIgnoreCase("print") ||
            name.equalsIgnoreCase(".printdec") ||
            name.equalsIgnoreCase("printdec") ||
            name.equalsIgnoreCase(".printhex") ||
            name.equalsIgnoreCase("printhex") ||
            name.equalsIgnoreCase(".byte") ||
            name.equalsIgnoreCase("byte") ||
            name.equalsIgnoreCase(".word") ||
            name.equalsIgnoreCase("word") ||
            name.equalsIgnoreCase(".rom") ||
            name.equalsIgnoreCase("rom") ||
            name.equalsIgnoreCase(".megarom") ||
            name.equalsIgnoreCase("megarom") ||
            name.equalsIgnoreCase(".basic") ||
            name.equalsIgnoreCase("basic") ||
            name.equalsIgnoreCase(".start") ||
            name.equalsIgnoreCase("start") ||
            name.equalsIgnoreCase(".search") ||
            name.equalsIgnoreCase("search") ||
            name.equalsIgnoreCase(".select") ||
            name.equalsIgnoreCase("select") ||
            name.equalsIgnoreCase(".subpage") ||
            name.equalsIgnoreCase("subpage") ||
            name.equalsIgnoreCase(".zilog") ||
            name.equalsIgnoreCase("zilog")) {
            return null;
        }
        
        // A relative label
        if (name.startsWith("@@")) {
            String lastAbsoluteLabel = getLastAbsoluteLabel(s);        
            if (lastAbsoluteLabel != null) {
                return lastAbsoluteLabel + "." + name.substring(2);
            }
        } else if (name.startsWith(".")) {
            String lastAbsoluteLabel = getLastAbsoluteLabel(s);        
            if (lastAbsoluteLabel != null) {
                return lastAbsoluteLabel + name;
            }
        }
        return name;
    }


    @Override
    public String symbolName(String name, SourceStatement s) {
        if (name.startsWith("@@")) {
            String lastAbsoluteLabel = getLastAbsoluteLabel(s);        
            if (lastAbsoluteLabel != null) {
                return lastAbsoluteLabel + "." + name.substring(2);
            }
        } else if (name.startsWith(".")) {
            String lastAbsoluteLabel = getLastAbsoluteLabel(s);        
            if (lastAbsoluteLabel != null) {
                return lastAbsoluteLabel + name;
            }
        }   
        return name;
    }
    
    
    List<List<String>> tokenizeFileLines(String fileName)
    {
        try {
            BufferedReader br = Resources.asReader(fileName);
            List<List<String>> tokenizedLines = new ArrayList<>();
            while(true) {
                String line = br.readLine();
                if (line == null) break;
                tokenizedLines.add(Tokenizer.tokenize(line));
            }
            return tokenizedLines;
        } catch (Exception e) {
            config.error("Cannot read file " + fileName);
            return null;            
        }
    }
    

    @Override
    public List<SourceStatement> parseLine(List<String> tokens, SourceLine sl, 
            SourceStatement s, SourceStatement previous, SourceFile source, CodeBase code) 
    {
        List<SourceStatement> l = new ArrayList<>();
        l.add(s);
        
        if (tokens.size()>=1 && (tokens.get(0).equalsIgnoreCase(".bios") || tokens.get(0).equalsIgnoreCase("bios"))) {
            tokens.remove(0);
            // Define all the bios calls:
            List<List<String>> tokenizedLines = tokenizeFileLines(biosCallsFileName);
            for(List<String> tokens2: tokenizedLines) {
                List<SourceStatement> l2 = config.lineParser.parse(tokens2, sl, source, -1, code, config);
                l.addAll(l2);
            }
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }
        if (tokens.size()>=1 && (tokens.get(0).equalsIgnoreCase(".search") || tokens.get(0).equalsIgnoreCase("search"))) {
            tokens.remove(0);
            // Define all the bios calls:
            List<List<String>> tokenizedLines = tokenizeFileLines(searchFileName);
            for(List<String> tokens2: tokenizedLines) {
                List<SourceStatement> l2 = config.lineParser.parse(tokens2, sl, source, -1, code, config);
                l.addAll(l2);
            }
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }        
        if (tokens.size()>=2 && (tokens.get(0).equalsIgnoreCase(".filename") || tokens.get(0).equalsIgnoreCase("filename"))) {
            tokens.remove(0);
            Expression filename_exp = config.expressionParser.parse(tokens, s, previous, code);
            if (filename_exp == null) {
                config.error("Cannot parse expression in "+sl.fileNameLineString()+": " + sl.line);
                return null;
            }
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }
        if (tokens.size()>=1 && (tokens.get(0).equalsIgnoreCase(".size") || tokens.get(0).equalsIgnoreCase("size"))) {
            tokens.remove(0);
            Expression size_exp = config.expressionParser.parse(tokens, s, previous, code);
            if (size_exp == null) {
                config.error("Cannot parse .size parameter in "+sl.fileNameLineString()+": " + sl.line);
                return null;
            }
            targetSizeInKB = size_exp.evaluateToInteger(s, code, true);
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }
        if (tokens.size()>=1 && (tokens.get(0).equalsIgnoreCase(".page") || tokens.get(0).equalsIgnoreCase("page"))) {
            tokens.remove(0);
            Expression page_exp = config.expressionParser.parse(tokens, s, previous, code);
            if (page_exp == null) {
                config.error("Cannot parse .page parameter in "+sl.fileNameLineString()+": " + sl.line);
                return null;
            }
            s.type = SourceStatement.STATEMENT_ORG;
            s.org = Expression.operatorExpression(Expression.EXPRESSION_MUL,
                    Expression.parenthesisExpression(page_exp, "(", config),
                    Expression.constantExpression(16*1024, false, true, config), config);
            // Since we are not producing compiled output, we ignore this directive
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }
        if (tokens.size()>=2 && (tokens.get(0).equalsIgnoreCase(".printtext") || tokens.get(0).equalsIgnoreCase("printtext"))) {
            toPrint.add(new PrintRecord("printtext", 
                    source.getStatements().get(source.getStatements().size()-1), 
                    Expression.constantExpression(tokens.get(1), config)));
            tokens.remove(0);
            tokens.remove(0);
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }
        if (tokens.size()>=2 && (tokens.get(0).equalsIgnoreCase(".printdec") || tokens.get(0).equalsIgnoreCase(".print") ||
                                 tokens.get(0).equalsIgnoreCase("printdec") || tokens.get(0).equalsIgnoreCase("print"))) {
            tokens.remove(0);
            Expression exp = config.expressionParser.parse(tokens, s, previous, code);
            toPrint.add(new PrintRecord("printdec", 
                    source.getStatements().get(source.getStatements().size()-1), 
                    exp));
            
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }
        if (tokens.size()>=2 && (tokens.get(0).equalsIgnoreCase(".printhex") || tokens.get(0).equalsIgnoreCase("printhex"))) {
            tokens.remove(0);
            Expression exp = config.expressionParser.parse(tokens, s, previous, code);
            toPrint.add(new PrintRecord("printhex", 
                    source.getStatements().get(source.getStatements().size()-1), 
                    exp));
            
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }
        if (tokens.size()>=1 && (tokens.get(0).equalsIgnoreCase(".byte") || tokens.get(0).equalsIgnoreCase("byte"))) {
            tokens.remove(0);
            s.type = SourceStatement.STATEMENT_DEFINE_SPACE;
            s.space = Expression.constantExpression(1, config);
            s.space_value = null;
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }
        if (tokens.size()>=1 && (tokens.get(0).equalsIgnoreCase(".word") || tokens.get(0).equalsIgnoreCase("word"))) {
            tokens.remove(0);
            s.type = SourceStatement.STATEMENT_DEFINE_SPACE;
            s.space = Expression.constantExpression(2, config);
            s.space_value = null;
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }
        if (tokens.size()>=1 && 
            (tokens.get(0).equalsIgnoreCase(".rom") || tokens.get(0).equalsIgnoreCase("rom") ||
             tokens.get(0).equalsIgnoreCase(".megarom") || tokens.get(0).equalsIgnoreCase("megarom"))) {
            String romToken = tokens.remove(0);
            romType = ROM_STANDARD;
            if (romToken.equalsIgnoreCase(".megarom") || romToken.equalsIgnoreCase("megarom")) {
                romType = 0;    // "konami" ROM
                pageSize = 8*1024;
                if (tokens.size()>=1) {
                    String romTypeToken = tokens.remove(0).toLowerCase();
                    switch (romTypeToken) {
                        case "konami":
                            break;
                        case "konamiscc":
                            romType = MEGAROM_KONAMI_SCC;
                            break;
                        case "ascii8":
                            romType = MEGAROM_ASCII8;
                            break;
                        case "ascii16":
                            romType = MEGAROM_ASCII16;
                            pageSize = 16*1024;
                            break;
                        default:
                            config.error("Unknown megaROM type in "+sl.fileNameLineString()+": " + romTypeToken);
                            return null;
                    }
                }
                // assume we are in the first page:
                currentPageEnd = pageSize;
            }
            
            // Generates a ROM header (and stores a pointer to the start address, to later modify with the .start directive):
            List<Expression> data = new ArrayList<>();
            romHeaderStatement = s;
            s.type = SourceStatement.STATEMENT_DATA_BYTES;
            s.data = data;
            data.add(Expression.constantExpression("AB", config));
            if (startAddressLabel == null) {
                data.add(Expression.constantExpression(0, config)); // start address place holder
                data.add(Expression.constantExpression(0, config)); 
            } else {
                data.add(Expression.operatorExpression(Expression.EXPRESSION_MOD, 
                        startAddressLabel, 
                        Expression.constantExpression(256, config), config)); 
                data.add(Expression.operatorExpression(Expression.EXPRESSION_DIV, 
                        startAddressLabel, 
                        Expression.constantExpression(256, config), config)); 
            }
            for(int i = 0;i<12;i++) data.add(Expression.constantExpression(0, config));
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }
        if (tokens.size()>=1 && (tokens.get(0).equalsIgnoreCase(".basic") || tokens.get(0).equalsIgnoreCase("basic"))) {
            tokens.remove(0);
            // Generates a BASIC header (and stores a pointer to the start address, to later modify with the .start directive):
            List<Expression> data = new ArrayList<>();
            basicHeaderStatement = s;
            s.type = SourceStatement.STATEMENT_DATA_BYTES;
            s.data = data;
            data.add(Expression.constantExpression(0xfe, true, false, config));
            data.add(Expression.constantExpression(0, config)); 
            data.add(Expression.constantExpression(0, config));             
            for(int i = 0;i<2;i++) data.add(Expression.constantExpression(0, config));
            if (startAddressLabel == null) {
                data.add(Expression.constantExpression(0, config)); // start address place holder
                data.add(Expression.constantExpression(0, config)); 
            } else {
                data.add(Expression.operatorExpression(Expression.EXPRESSION_MOD, 
                        startAddressLabel, 
                        Expression.constantExpression(256, config), config)); 
                data.add(Expression.operatorExpression(Expression.EXPRESSION_DIV, 
                        startAddressLabel, 
                        Expression.constantExpression(256, config), config)); 
            }
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }        
        if (tokens.size()>=2 && (tokens.get(0).equalsIgnoreCase(".start") || tokens.get(0).equalsIgnoreCase("start"))) {
            tokens.remove(0);
            Expression exp = config.expressionParser.parse(tokens, s, previous, code);
            if (exp == null) {
                config.error("Cannot parse expression in "+sl.fileNameLineString()+": " + sl.line);
                return null;
            }
            startAddressLabel = exp;
            if (romHeaderStatement != null) {
                romHeaderStatement.data.set(1,Expression.operatorExpression(Expression.EXPRESSION_MOD, 
                        startAddressLabel, 
                        Expression.constantExpression(256, config), config)); 
                romHeaderStatement.data.set(2,Expression.operatorExpression(Expression.EXPRESSION_DIV, 
                        startAddressLabel, 
                        Expression.constantExpression(256, config), config)); 
            }
            if (basicHeaderStatement != null) {
                basicHeaderStatement.data.set(5,Expression.operatorExpression(Expression.EXPRESSION_MOD, 
                        startAddressLabel, 
                        Expression.constantExpression(256, config), config)); 
                basicHeaderStatement.data.set(6,Expression.operatorExpression(Expression.EXPRESSION_DIV, 
                        startAddressLabel, 
                        Expression.constantExpression(256, config), config)); 
            }
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("=")) {
            // This is like an equ, but with a variable that changes value throughout parsing.
            // This only makes sense in eager execution, so, we check for that:
            if (!config.eagerMacroEvaluation) {
                config.error("Non final variable defined in lazy evaluation mode at " + sl);
                return null;
            }
            
            tokens.remove(0);
            if (!config.lineParser.parseEqu(tokens, sl, s, previous, source, code)) return null;
            Integer value = s.label.exp.evaluateToInteger(s, code, false);
            if (value == null) {
                config.error("Cannot resolve eager variable in " + sl);
                return null;
            }
            Expression exp = Expression.constantExpression(value, config);
            SourceConstant c = code.getSymbol(s.label.originalName);
            c.clearCache();
            c.exp = exp;            
            s.label.resolveEagerly = true;
            
            // these variables should not be part of the source code:
            l.clear();
            return l;
        }        
        if (tokens.size() >= 2 && (tokens.get(0).equalsIgnoreCase(".wav") || tokens.get(0).equalsIgnoreCase("wav"))) {
            tokens.remove(0);
            // just ignore
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }        
        if (tokens.size() >= 2 && (tokens.get(0).equalsIgnoreCase(".cas") || tokens.get(0).equalsIgnoreCase("cas"))) {
            tokens.remove(0);
            // just ignore
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }
        if (tokens.size()>=1 && (tokens.get(0).equalsIgnoreCase(".select") || tokens.get(0).equalsIgnoreCase("select"))) {
            tokens.remove(0);
            Expression page = config.expressionParser.parse(tokens, s, previous, code);
            if (page == null) {
                config.error("Cannot parse expression in "+sl.fileNameLineString()+": " + sl.line);
                return null;
            }
            if (tokens.isEmpty() || !tokens.remove(0).equalsIgnoreCase("at")) {
                config.error("Missing token 'at' in "+sl.fileNameLineString()+": " + sl.line);
                return null;
            }
            Expression address = config.expressionParser.parse(tokens, s, previous, code);
            if (address == null) {
                config.error("Cannot parse expression in "+sl.fileNameLineString()+": " + sl.line);
                return null;
            }
            
            if (romType != ROM_STANDARD) {
                // generate "select" code:
                if (page.isRegister(code) && page.registerOrFlagName.equals("a")) {
                    // ld (address), a:
                    s.type = SourceStatement.STATEMENT_CPUOP;
                    List<Expression> sArguments = new ArrayList<>();
                    if (config.opParser.indirectionsOnlyWithSquareBrackets) {
                        sArguments.add(Expression.parenthesisExpression(address, "[", config));
                    } else {
                        sArguments.add(Expression.parenthesisExpression(address, "(", config));
                    }
                    sArguments.add(Expression.symbolExpression("a", s, code, config));
                    List<CPUOp> op_l = config.opParser.parseOp("ld", sArguments, s, previous, code);
                    if (op_l == null || op_l.size() != 1) {
                        config.error("Error creating 'ld (<address>),a' instruction in "+sl.fileNameLineString()+": " + sl.line);
                        return null;
                    }
                    s.op = op_l.get(0);

                } else {
                    // push af; ld a, page; ld (address), a; pop af:
                    SourceStatement s1 = new SourceStatement(SourceStatement.STATEMENT_CPUOP, sl, source, config);
                    List<Expression> s1Arguments = new ArrayList<>();
                    s1Arguments.add(Expression.symbolExpression("af", s1, code, config));
                    List<CPUOp> op_l = config.opParser.parseOp("push", s1Arguments, s1, previous, code);
                    if (op_l == null || op_l.size() != 1) {
                        config.error("Error creating 'push af' instruction in "+sl.fileNameLineString()+": " + sl.line);
                        return null;
                    }
                    s1.op = op_l.get(0);
                    l.add(0, s1);

                    SourceStatement s2 = new SourceStatement(SourceStatement.STATEMENT_CPUOP, sl, source, config);
                    List<Expression> s2Arguments = new ArrayList<>();
                    s2Arguments.add(Expression.symbolExpression("a", s2, code, config));
                    s2Arguments.add(page);
                    op_l = config.opParser.parseOp("ld", s2Arguments, s2, previous, code);
                    if (op_l == null || op_l.size() != 1) {
                        config.error("Error creating 'ld a,<page>' instruction in "+sl.fileNameLineString()+": " + sl.line);
                        return null;
                    }
                    s2.op = op_l.get(0);
                    l.add(1, s2);

                    s.type = SourceStatement.STATEMENT_CPUOP;
                    List<Expression> sArguments = new ArrayList<>();
                    if (config.opParser.indirectionsOnlyWithSquareBrackets) {
                        sArguments.add(Expression.parenthesisExpression(address, "[", config));
                    } else {
                        sArguments.add(Expression.parenthesisExpression(address, "(", config));
                    }
                    sArguments.add(Expression.symbolExpression("a", s, code, config));
                    op_l = config.opParser.parseOp("ld", sArguments, s, previous, code);
                    if (op_l == null || op_l.size() != 1) {
                        config.error("Error creating 'ld (<address>),a' instruction in "+sl.fileNameLineString()+": " + sl.line);
                        return null;
                    }
                    s.op = op_l.get(0);

                    SourceStatement s4 = new SourceStatement(SourceStatement.STATEMENT_CPUOP, sl, source, config);
                    List<Expression> s4Arguments = new ArrayList<>();
                    s4Arguments.add(Expression.symbolExpression("af", s4, code, config));
                    op_l = config.opParser.parseOp("pop", s4Arguments, s4, previous, code);
                    if (op_l == null || op_l.size() != 1) {
                        config.error("Error creating 'pop af' instruction in "+sl.fileNameLineString()+": " + sl.line);
                        return null;
                    }
                    s4.op = op_l.get(0);
                    l.add(s4);
                }
            }            
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }    
        if (tokens.size()>=1 && (tokens.get(0).equalsIgnoreCase(".subpage") || tokens.get(0).equalsIgnoreCase("subpage"))) {
            tokens.remove(0);
            Expression page = config.expressionParser.parse(tokens, s, previous, code);
            if (page == null) {
                config.error("Cannot parse expression in "+sl.fileNameLineString()+": " + sl.line);
                return null;
            }
            if (tokens.isEmpty() || !tokens.remove(0).equalsIgnoreCase("at")) {
                config.error("Missing token 'at' in "+sl.fileNameLineString()+": " + sl.line);
                return null;
            }
            Expression address = config.expressionParser.parse(tokens, s, previous, code);
            if (address == null) {
                config.error("Cannot parse expression in "+sl.fileNameLineString()+": " + sl.line);
                return null;
            }
            // just set an org, and record it as a page definition org:
            // - org (address / pageSize) * pageSize
            s.type = SourceStatement.STATEMENT_ORG;
            s.org = Expression.operatorExpression(Expression.EXPRESSION_MUL, 
                        Expression.parenthesisExpression(
                            Expression.operatorExpression(Expression.EXPRESSION_DIV, 
                                address,
                                Expression.constantExpression(pageSize, config), config), 
                            "(", config),
                        Expression.constantExpression(pageSize, config), config);
            int raw_page = page.evaluateToInteger(s, code, false);
            while(pageDefinitions.size() < raw_page+1) pageDefinitions.add(null);
            pageDefinitions.set(raw_page, s);
            
            // update "currentPageEnd":
            currentPageEnd += pageSize;
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }        
        if (tokens.size() >= 1 && (tokens.get(0).equalsIgnoreCase(".zilog") || tokens.get(0).equalsIgnoreCase("zilog"))) {
            tokens.remove(0);
            config.opParser.indirectionsOnlyWithSquareBrackets = false;
            config.opParser.indirectionsOnlyWithParenthesis = true;
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }

        // weird syntax that for some reason asMSX swallows (undocumented):
        // if a line is all dashes, it's ignored:
        {
            boolean allDashes = true;
            for(String token:tokens) {
                if (!token.equals("-") && !token.equals("--")) {
                    allDashes = false;
                    break;
                }
            }
            if (allDashes) {
                String newToken = ";";
                for(String token:tokens) newToken += token;
                tokens.clear();
                tokens.add(newToken);
                if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
            }
        }
        
        config.error("ASMSXDialect cannot parse line in "+sl.fileNameLineString()+": " + sl.line);
        return null;
    }


    @Override
    public Number evaluateExpression(String functionName, List<Expression> args, SourceStatement s, CodeBase code, boolean silent)
    {
        if ((functionName.equalsIgnoreCase(".random") || 
             functionName.equalsIgnoreCase("random")) && args.size() == 1) {
            Integer range = args.get(0).evaluateToInteger(s, code, silent);
            if (range == null) return null;
            return r.nextInt(range);
        }
        if ((functionName.equalsIgnoreCase(".sin") || 
             functionName.equalsIgnoreCase("sin")) && args.size() == 1) {
            Object range = args.get(0).evaluate(s, code, silent);
            if (range == null) return null;
            if (range instanceof Integer) {
                return Math.sin((Integer)range);
            } else if (range instanceof Double) {
                return Math.sin((Double)range); 
            } else {
                return null;
            }
        }
        if ((functionName.equalsIgnoreCase(".cos") || 
             functionName.equalsIgnoreCase("cos")) && args.size() == 1) {
            Object range = args.get(0).evaluate(s, code, silent);
            if (range == null) return null;
            if (range instanceof Integer) {
                return Math.cos((Integer)range);
            } else if (range instanceof Double) {
                return Math.cos((Double)range); 
            } else {
                return null;
            }
        }
        if ((functionName.equalsIgnoreCase(".fix") || 
             functionName.equalsIgnoreCase("fix")) && args.size() == 1) {
            Object input = args.get(0).evaluate(s, code, silent);
            if (input == null) return null;
            if (input instanceof Integer) {
                return (int)(((Integer)input)*256);
            } else if (input instanceof Double) {
                return (int)(((Double)input)*256);
            } else {
                return null;
            }
        }
        return null;
    }
    
    
    @Override
    public boolean expressionEvaluatesToIntegerConstant(String functionName) {
        if (functionName.equalsIgnoreCase(".sin") || 
            functionName.equalsIgnoreCase("sin")) {
            return false;
        }
        if (functionName.equalsIgnoreCase(".cos") || 
            functionName.equalsIgnoreCase("cos")) {
            return false;
        }

        return true;
    }
    
    
    @Override    
    public void performAnyInitialActions(CodeBase code) {
        SourceConstant sc = new SourceConstant("pi", "pi", Expression.constantExpression(Math.PI, config), null, config);
        code.addSymbol("pi", sc);
    }
    
    
    @Override
    public boolean performAnyFinalActions(CodeBase code)
    {                
        if (basicHeaderStatement != null) {
            // Look for the very first org (and make sure the basic header is BEFORE the org):
            SourceStatement s = code.getMain().getNextStatementTo(null, code);
            while(s != null) {
                if (s.type == SourceStatement.STATEMENT_ORG) {
                    // set the load address:
                    basicHeaderStatement.data.set(1, Expression.operatorExpression(Expression.EXPRESSION_MOD, 
                            Expression.parenthesisExpression(s.org, "(", config), 
                            Expression.constantExpression(256, config), config)); 
                    basicHeaderStatement.data.set(2, Expression.operatorExpression(Expression.EXPRESSION_DIV, 
                            Expression.parenthesisExpression(s.org, "(", config), 
                            Expression.constantExpression(256, config), config));
                    
                    // found it! We should insert the basic header right before this!
                    basicHeaderStatement.source.getStatements().remove(basicHeaderStatement);
                    int idx = s.source.getStatements().indexOf(s);
                    s.source.getStatements().add(idx, basicHeaderStatement);                    
                    break;
                } else if (s.type == SourceStatement.STATEMENT_CPUOP) {
                    // give up
                    break;
                }
                s = s.source.getNextStatementTo(s, code);
            }
        }

        // start/load addresses for rom/basic headers if not yet set:
        if (basicHeaderStatement != null && startAddressLabel == null) {
            // Look for the very first assembler instruction:
            SourceStatement s = code.getMain().getNextStatementTo(null, code);
            while(s != null) {
                if (s.type == SourceStatement.STATEMENT_CPUOP) {
                    // found it!
                    basicHeaderStatement.data.set(5,Expression.operatorExpression(Expression.EXPRESSION_MOD, 
                            Expression.constantExpression(s.getAddress(code), false, true, config), 
                            Expression.constantExpression(256, config), config)); 
                    basicHeaderStatement.data.set(6,Expression.operatorExpression(Expression.EXPRESSION_DIV, 
                            Expression.constantExpression(s.getAddress(code), false, true, config), 
                            Expression.constantExpression(256, config), config)); 
                    break;
                }
                s = s.source.getNextStatementTo(s, code);
            }
        }
                
        {
            SourceStatement firstGeneratingBytes = null;
            SourceStatement lastGeneratingBytes = null;
            SourceStatement s = code.getMain().getNextStatementTo(null, code);
            while(s != null) {
                if (s.type == SourceStatement.STATEMENT_INCLUDE && !s.include.getStatements().isEmpty()) {
                    s = s.include.getStatements().get(0);
                }
                if (s.sizeInBytes(code, false, true, false) > 0) {
                    if (firstGeneratingBytes == null) firstGeneratingBytes = s;
                    lastGeneratingBytes = s;
                }
                s = s.source.getNextStatementTo(s, code);
            }

            // "org/page" in asMSX work different than in some other assemblers, and we need to fill with zeros 
            // the space until reaching the address of the org/page! (also, if you have two orgs with the same
            // address, the latter code will overwrite the first:
            if (firstGeneratingBytes != null && lastGeneratingBytes != null) {
                SourceStatement previous = null;
                s = firstGeneratingBytes;
                while(s != lastGeneratingBytes) {
                    if (s.type == SourceStatement.STATEMENT_INCLUDE && !s.include.getStatements().isEmpty()) {
                        s = s.include.getStatements().get(0);
                    }
                    if (previous != null && s.type == SourceStatement.STATEMENT_ORG) {
                        // make sure we don't do it for the first org:
                        if (basicHeaderStatement != previous) {
                            int previousAddress = previous.getAddress(code) + previous.sizeInBytes(code, false, true, false);
                            int orgAddress = s.org.evaluateToInteger(s, code, false);
                            int pad = 0;
                            if (orgAddress > previousAddress) pad = orgAddress - previousAddress;
                            
                            // Check to see if this is an "org" statement defining a page:
                            for(int page = 0;page<pageDefinitions.size(); page++) {
                                if (pageDefinitions.get(page) == s) {
                                    // it is! Pad to fill the page:
                                    if (page > 0 && pageDefinitions.get(page - 1) != null) {
                                        int previousPageStart = pageDefinitions.get(page - 1).org.evaluateToInteger(s, code, false);
                                        pad = pageSize - (previousAddress - previousPageStart);
                                    }
                                }
                            }
                            
                            if (pad > 0) {
                                // we need to insert filler space:
                                config.debug("asMSX: pad: " + pad + " to reach " + orgAddress);
                                SourceStatement padStatement = new SourceStatement(SourceStatement.STATEMENT_DEFINE_SPACE, null, lastGeneratingBytes.source, config);
                                padStatement.space = Expression.constantExpression(pad, config);
                                padStatement.space_value = Expression.constantExpression(0, config);
                                previous.source.addStatement(previous.source.getStatements().indexOf(previous)+1, padStatement);                            
                            }
                        }
                    }
                    previous = s;
                    s = s.source.getNextStatementTo(s, code);                    
                }
            }

            if (romHeaderStatement != null || targetSizeInKB > 0) {
                // If a ROM is generated, asMSX fills the rom all the way to the nearest multiple of 8192 
                // (this is not documented, but it is what it does, based on inspecting its source code):
                // Find the last instruction that generates actual bytes in the ROM:
                if (firstGeneratingBytes != null && lastGeneratingBytes != null) {
                    int start_address = firstGeneratingBytes.getAddress(code);
                    int end_address = lastGeneratingBytes.getAddress(code) + lastGeneratingBytes.sizeInBytes(code, false, true, false);
                    int size = end_address - start_address;
                    int target_size = targetSizeInKB > 0 ? targetSizeInKB * 1024 : (((size + 8191) / 8192) * 8192);
                    int pad = target_size - size;
                    config.debug("asMSX: start_address: " + start_address);
                    config.debug("asMSX: end_address: " + end_address);
                    config.debug("asMSX: size: " + size);
                    config.debug("asMSX: target_size: " + target_size);
                    config.debug("asMSX: pad: " + pad);
                    SourceStatement padStatement = new SourceStatement(SourceStatement.STATEMENT_DEFINE_SPACE, null, lastGeneratingBytes.source, config);
                    padStatement.space = Expression.constantExpression(pad, config);
                    padStatement.space_value = Expression.constantExpression(0, config);
                    lastGeneratingBytes.source.addStatement(lastGeneratingBytes.source.getStatements().indexOf(lastGeneratingBytes)+1, padStatement);
                }
            } else if (basicHeaderStatement != null) {
                // set the end address:
                if (lastGeneratingBytes != null) {
                    int end_address = lastGeneratingBytes.getAddress(code) + lastGeneratingBytes.sizeInBytes(code, false, true, false);
                    basicHeaderStatement.data.set(3,Expression.operatorExpression(Expression.EXPRESSION_MOD, 
                            Expression.constantExpression(end_address-1, false, true, config), 
                            Expression.constantExpression(256, config), config)); 
                    basicHeaderStatement.data.set(4,Expression.operatorExpression(Expression.EXPRESSION_DIV, 
                            Expression.constantExpression(end_address-1, false, true, config), 
                            Expression.constantExpression(256, config), config)); 
                }
            } 
        }
        
        
        for(PrintRecord pr:toPrint) {
            switch(pr.keyword) {
                case "printtext":
                    config.info(pr.exp.stringConstant);
                    break;
                case "printdec":
                {
                    SourceFile f = pr.previousStatement.source;
                    int idx = f.getStatements().indexOf(pr.previousStatement);
                    SourceStatement s = null;
                    if (f.getStatements().size() > idx+1) s = f.getStatements().get(idx+1);
                    Integer value = pr.exp.evaluateToInteger(s, code, false);
                    if (value == null) {
                        config.error("Cannot evaluate expression " + pr.exp);
                    } else {
                        config.info("" + value);
                    }
                    break;
                }
                case "printhex":
                {
                    SourceFile f = pr.previousStatement.source;
                    int idx = f.getStatements().indexOf(pr.previousStatement);
                    SourceStatement s = null;
                    if (f.getStatements().size() > idx+1) s = f.getStatements().get(idx+1);
                    Integer value = pr.exp.evaluateToInteger(s, code, false);
                    if (value == null) {
                        config.error("Cannot evaluate expression " + pr.exp);
                    } else {
                        config.info("" + Tokenizer.toHexWord(value));
                    }
                    break;
                }
            }
        }
        
        // set this back to normal setting, so the optimizer patterns are properly parsed:
        config.opParser.indirectionsOnlyWithSquareBrackets = false;
        
        return true;
    }
    
}
