/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package parser.dialects;


import cl.MDLConfig;
import code.CodeBase;
import code.Expression;
import code.SourceConstant;
import code.SourceFile;
import code.SourceStatement;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import parser.SourceLine;
import parser.SourceMacro;
import parser.Tokenizer;
import util.Resources;

/**
 *
 * @author santi
 */
public class ASMSXDialect implements Dialect {
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

    String lastAbsoluteLabel = null;
    SourceStatement romHeaderStatement = null;
    SourceStatement basicHeaderStatement = null;
    Expression startAddressLabel = null;
    
    // Addresses are not resolved until the very end, so, when printing values, we just queue them up here, and
    // print them all at the very end:
    List<PrintRecord> toPrint = new ArrayList<>();
    

    public ASMSXDialect(MDLConfig a_config)
    {
        config = a_config;

        config.lineParser.KEYWORD_ORG = ".org";
        config.lineParser.KEYWORD_INCLUDE = ".include";
        config.lineParser.KEYWORD_INCBIN = ".incbin";
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

        config.warningJpHlWithParenthesis = false;
        config.lineParser.keywordsHintingALabel.add("=");
        
        config.expressionParser.dialectFunctions.add(".random");
        config.expressionParser.dialectFunctions.add("random");
        config.expressionParser.dialectFunctions.add(".fix");
        config.expressionParser.dialectFunctions.add("fix");
        config.expressionParser.dialectFunctions.add(".sin");
        config.expressionParser.dialectFunctions.add("sin");
        config.expressionParser.dialectFunctions.add(".cos");
        config.expressionParser.dialectFunctions.add("cos");
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
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("=")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".wav")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("wav")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".cas")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("cas")) return true;
        
        // weird syntax that for some reason asMSX swallows (undocumented):
        // if a line is all dashes, it's ignored:
        {
            boolean allDashes = true;
            for(String token:tokens) {
                if (!token.equals("-")) {
                    allDashes = false;
                    break;
                }
            }
            if (allDashes) return true;
        }
        return false;
    }


    @Override
    public String newSymbolName(String name, Expression value) {
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
            name.equalsIgnoreCase(".basic") ||
            name.equalsIgnoreCase("basic") ||
            name.equalsIgnoreCase(".start") ||
            name.equalsIgnoreCase("start")) {
            return null;
        }
        if (name.startsWith("@@")) {
            return lastAbsoluteLabel + "." + name.substring(2);
        } else if (name.startsWith(".")) {
            return lastAbsoluteLabel + "." + name.substring(1);
        } else {
            // When a name has "CURRENT_ADDRESS" as its value, it means it's a label.
            // If it does not start by ".", then it's an absolute label:
            if (value != null &&
                value.type == Expression.EXPRESSION_SYMBOL &&
                value.symbolName.equalsIgnoreCase(CodeBase.CURRENT_ADDRESS)) {
                lastAbsoluteLabel = name;
            }
        }
        return name;
    }


    @Override
    public String symbolName(String name) {
        if (name.startsWith("@@")) {
            return lastAbsoluteLabel + "." + name.substring(2);
        } else if (name.startsWith(".")) {
            return lastAbsoluteLabel + "." + name.substring(1);
        } else {
            return name;
        }
    }
    
    
    String []parseBiosCallLine(String line)
    {
        String tokens[] = new String[2]; // name + address
        StringTokenizer st = new StringTokenizer(line, ": \t");
        if (!st.hasMoreTokens()) {
            return null;
        }
        tokens[0] = st.nextToken();
        if (!st.hasMoreTokens()) {
            return null;
        }
        String equ = st.nextToken();
        if (!equ.equalsIgnoreCase("equ")) {
            return null;
        }
        if (!st.hasMoreTokens()) {
            return null;
        }
        tokens[1] = st.nextToken();
        return tokens;
    }
    
    
    List<String []>loadBiosCalls()
    {
        try (BufferedReader br = Resources.asReader(biosCallsFileName)) {
            return IOUtils.readLines(br)
                    .stream()
                    .filter(line -> !line.trim().isEmpty())
                    .filter(line -> !Tokenizer.isSingleLineComment(line))
                    .map(line -> parseBiosCallLine(line))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            config.error("Cannot read bios calls file "+biosCallsFileName);
            return null;
        }
    }
    

    @Override
    public List<SourceStatement> parseLine(List<String> tokens, SourceLine sl, SourceStatement s, SourceFile source, CodeBase code) 
    {
        List<SourceStatement> l = new ArrayList<>();
        l.add(s);
        
        if (tokens.size()>=1 && (tokens.get(0).equalsIgnoreCase(".bios") || tokens.get(0).equalsIgnoreCase("bios"))) {
            tokens.remove(0);
            // Define all the bios calls:
            List<String []> biosCalls = loadBiosCalls();
            if (biosCalls == null) {
                config.error("Cannot read bios calls file in " + sl.fileNameLineString() + ": " + sl.line);
                return null;                
            }
            for(String []biosCall:biosCalls) {
                SourceStatement biosCallStatement = new SourceStatement(SourceStatement.STATEMENT_CONSTANT, sl, source);
                int address = Tokenizer.parseHex(biosCall[1]);
                biosCallStatement.label = new SourceConstant(biosCall[0], address, Expression.constantExpression(address, config), biosCallStatement);
                code.addSymbol(biosCall[0], biosCallStatement.label);
                l.add(biosCallStatement);
            }
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }
        if (tokens.size()>=2 && (tokens.get(0).equalsIgnoreCase(".filename") || tokens.get(0).equalsIgnoreCase("filename"))) {
            tokens.remove(0);
            Expression filename_exp = config.expressionParser.parse(tokens, s, code);
            if (filename_exp == null) {
                config.error("Cannot parse expression in "+sl.fileNameLineString()+": " + sl.line);
                return null;
            }
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }
        if (tokens.size()>=1 && (tokens.get(0).equalsIgnoreCase(".size") || tokens.get(0).equalsIgnoreCase("size"))) {
            tokens.remove(0);
            Expression size_exp = config.expressionParser.parse(tokens, s, code);
            if (size_exp == null) {
                config.error("Cannot parse .size parameter in "+sl.fileNameLineString()+": " + sl.line);
                return null;
            }
            // Since we are not producing compiled output, we ignore this directive
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }
        if (tokens.size()>=1 && (tokens.get(0).equalsIgnoreCase(".page") || tokens.get(0).equalsIgnoreCase("page"))) {
            tokens.remove(0);
            Expression page_exp = config.expressionParser.parse(tokens, s, code);
            if (page_exp == null) {
                config.error("Cannot parse .page parameter in "+sl.fileNameLineString()+": " + sl.line);
                return null;
            }
            s.type = SourceStatement.STATEMENT_ORG;
            s.org = Expression.operatorExpression(Expression.EXPRESSION_MUL,
                    Expression.parenthesisExpression(page_exp, config),
                    Expression.constantExpression(16*1024, config), config);
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
            Expression exp = config.expressionParser.parse(tokens, s, code);
            toPrint.add(new PrintRecord("printdec", 
                    source.getStatements().get(source.getStatements().size()-1), 
                    exp));
            
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }
        if (tokens.size()>=2 && (tokens.get(0).equalsIgnoreCase(".printhex") || tokens.get(0).equalsIgnoreCase("printhex"))) {
            tokens.remove(0);
            Expression exp = config.expressionParser.parse(tokens, s, code);
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
        if (tokens.size()>=1 && (tokens.get(0).equalsIgnoreCase(".rom") || tokens.get(0).equalsIgnoreCase("rom"))) {
            tokens.remove(0);
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
            data.add(Expression.constantExpression(0xfe, true, config));
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
            // the header of a basic program should not use any space, so we add an org statement afterwards to compensate for its space:
//            SourceStatement auxiliarOrg = new SourceStatement(SourceStatement.STATEMENT_ORG, sl, source, null);
//            auxiliarOrg.org = Expression.operatorExpression(Expression.EXPRESSION_SUB, 
//                    Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, code, config), 
//                    Expression.constantExpression(7, config), config);
//            l.add(auxiliarOrg);
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }        
        if (tokens.size()>=2 && (tokens.get(0).equalsIgnoreCase(".start") || tokens.get(0).equalsIgnoreCase("start"))) {
            tokens.remove(0);
            Expression exp = config.expressionParser.parse(tokens, s, code);
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
            s.label.resolveEagerly = true;
            if (!config.lineParser.parseEqu(tokens, sl, s, source, code)) return null;
            s.label.clearCache();
            Integer value = s.label.exp.evaluateToInteger(s, code, false);
            if (value == null) {
                config.error("Cannot resolve eager variable in " + sl);
                return null;
            }
            s.label.exp = Expression.constantExpression(value, config);
            
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

        // weird syntax that for some reason asMSX swallows (undocumented):
        // if a line is all dashes, it's ignored:
        {
            boolean allDashes = true;
            for(String token:tokens) {
                if (!token.equals("-")) {
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
            Number range = args.get(0).evaluate(s, code, silent);
            if (range == null) return null;
            return Math.sin(range.doubleValue());
        }
        if ((functionName.equalsIgnoreCase(".cos") || 
             functionName.equalsIgnoreCase("cos")) && args.size() == 1) {
            Number range = args.get(0).evaluate(s, code, silent);
            if (range == null) return null;
            return Math.cos(range.doubleValue());
        }
        if ((functionName.equalsIgnoreCase(".fix") || 
             functionName.equalsIgnoreCase("fix")) && args.size() == 1) {
            Number input = args.get(0).evaluate(s, code, silent);
            if (input == null) return null;
            return (int)(input.doubleValue()*256);
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
        SourceConstant sc = new SourceConstant("pi", null, Expression.constantExpression(Math.PI, config), null);
        code.addSymbol("pi", sc);
    }
    
    
    @Override
    public boolean performAnyFinalActions(CodeBase code)
    {
        // start/load addresses for rom/basic headers if not yet set:
        if (basicHeaderStatement != null && startAddressLabel == null) {
            // Look for the very first assembler instruction:
            SourceStatement s = code.getMain().getNextStatementTo(null, code);
            while(s != null) {
                if (s.type == SourceStatement.STATEMENT_CPUOP) {
                    // found it!
                    basicHeaderStatement.data.set(5,Expression.operatorExpression(Expression.EXPRESSION_MOD, 
                            Expression.constantExpression(s.getAddress(code), config), 
                            Expression.constantExpression(256, config), config)); 
                    basicHeaderStatement.data.set(6,Expression.operatorExpression(Expression.EXPRESSION_DIV, 
                            Expression.constantExpression(s.getAddress(code), config), 
                            Expression.constantExpression(256, config), config)); 
                    break;
                }
                s = s.source.getNextStatementTo(s, code);
            }
        }
        
        if (basicHeaderStatement != null) {
            // Look for the very first org (and make sure the basic header is BEFORE the org):
            SourceStatement s = code.getMain().getNextStatementTo(null, code);
            while(s != null) {
                if (s.type == SourceStatement.STATEMENT_ORG) {
                    // found it! We should insert the basic header right before this!
                    basicHeaderStatement.source.getStatements().remove(basicHeaderStatement);
                    int idx = s.source.getStatements().indexOf(s);
                    s.source.getStatements().add(idx, basicHeaderStatement);
                    
                    // set the load address:
                    basicHeaderStatement.data.set(1, Expression.operatorExpression(Expression.EXPRESSION_MOD, 
                            Expression.parenthesisExpression(s.org, config), 
                            Expression.constantExpression(256, config), config)); 
                    basicHeaderStatement.data.set(2, Expression.operatorExpression(Expression.EXPRESSION_DIV, 
                            Expression.parenthesisExpression(s.org, config), 
                            Expression.constantExpression(256, config), config));
                    break;
                }
                s = s.source.getNextStatementTo(s, code);
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
        
        return true;
    }
    
}
