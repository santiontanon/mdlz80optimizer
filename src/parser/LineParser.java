/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package parser;

import cl.MDLConfig;
import code.Expression;
import code.SourceConstant;
import code.SourceFile;
import code.SourceStatement;
import code.CodeBase;
import code.CPUOp;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LineParser {
    MDLConfig config;
    CodeBaseParser codeBaseParser;
    
    // for local labels:
    String labelPrefix = "";
    List<String> labelPrefixStack = new ArrayList<>();
    
    public LineParser(MDLConfig a_config, CodeBaseParser a_codeBaseParser)
    {
        config = a_config;
        codeBaseParser = a_codeBaseParser;
    }
    
    
    public void pushLabelPrefix(String a_lp)
    {
        labelPrefixStack.add(0,labelPrefix);
        labelPrefix = a_lp;
    }

    
    public void popLabelPrefix()
    {
        labelPrefix = labelPrefixStack.remove(0);
    }


    public SourceStatement parse(List<String> tokens, String line, int lineNumber, 
            SourceFile f, CodeBase code, MDLConfig config) throws Exception
    {
        // SourceStatement s = new SourceStatement(SourceStatement.STATEMENT_NONE, source, lineNumber, code.getAddress());
        SourceStatement s = new SourceStatement(SourceStatement.STATEMENT_NONE, f, lineNumber, null);
        
        if (!parseInternal(tokens, line, lineNumber, s, f, code)) return null;        
        return s;
    }    
    

    boolean parseInternal(List<String> tokens, String line, int lineNumber, SourceStatement s, SourceFile source, CodeBase code) throws Exception
    {
        if (tokens.isEmpty()) return true;
        
        String token = tokens.get(0);
        
        // The very first thing is to check if there is a label:
        if (tokens.size() >= 2 &&
                   Tokenizer.isSymbol(token) &&
                   tokens.get(1).equals(":")) {
            Expression exp = Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, code);

            if (tokens.size() >= 3) {
                if (!tokens.get(2).equalsIgnoreCase("equ")) {
                    tokens.remove(0);
                    tokens.remove(0);

                    SourceConstant c = new SourceConstant(labelPrefix+token, null, exp, s); 
                    s.type = SourceStatement.STATEMENT_NONE;
                    s.label = c;
                    code.addSymbol(c.name, c);
                    token = tokens.get(0);
                }
            } else {
                tokens.remove(0);
                tokens.remove(0);

                SourceConstant c = new SourceConstant(labelPrefix+token, null, exp, s); 
                s.type = SourceStatement.STATEMENT_NONE;
                s.label = c;
                code.addSymbol(c.name, c);
                return parseRestofTheLine(tokens, line, lineNumber, s, source);
            }
        } else if (Tokenizer.isSymbol(token)) {
            if (line.startsWith(token)) {
                if (tokens.size() == 1 || tokens.get(1).startsWith(";")) {
                    // it is just a label without colon:
                    if (config.warningLabelWithoutColon) {
                        config.warn("Label defined without a colon in " + 
                                source.fileName + ", " + lineNumber + ": " + line);
                    }
                    Expression exp = Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, code);
                    int address = exp.evaluate(s, code, false);
                    tokens.remove(0);

                    SourceConstant c = new SourceConstant(labelPrefix+token, address, exp, s); 
                    s.type = SourceStatement.STATEMENT_NONE;
                    s.label = c;
                    code.addSymbol(c.name, c);
                    return parseRestofTheLine(tokens, line, lineNumber, s, source);   
                } else if (tokens.size() >= 3 && tokens.get(1).equalsIgnoreCase("equ")) {
                    // equ without a colon (provide warning):
                    if (config.warningLabelWithoutColon) {
                        config.warn("Label defined without a colon in " + 
                                source.fileName + ", " + lineNumber + ": " + line);
                    }
                    tokens.remove(0);
                    tokens.remove(0);
                    return parseEqu(tokens, token, line, lineNumber, s, source, code);
                }
            }
        }
        
        if (token.equalsIgnoreCase("org")) {
            tokens.remove(0);
            return parseOrg(tokens, line, lineNumber, s, source, code);
            
        } else if (token.equalsIgnoreCase("include")) {
            tokens.remove(0);
            return parseInclude(tokens, line, lineNumber, s, source, code);
            
        } else if (token.equals("incbin")) {
            tokens.remove(0);
            return parseIncbin(tokens, line, lineNumber, s, source, code);
            
        } else if (tokens.size() >= 4 &&
                   Tokenizer.isSymbol(token) &&
                   tokens.get(1).equals(":") &&
                   tokens.get(2).equalsIgnoreCase("equ")) {
            tokens.remove(0);
            tokens.remove(0);
            tokens.remove(0);
            return parseEqu(tokens, token, line, lineNumber, s, source, code);
            
        } else if (tokens.size() >= 2 &&
                   (token.equalsIgnoreCase("db") ||
                    token.equalsIgnoreCase("dw") ||
                    token.equalsIgnoreCase("dd"))) {
            tokens.remove(0);
            return parseData(tokens, token, line, lineNumber, s, source, code);

        } else if (tokens.size() >= 2 && token.equalsIgnoreCase("ds")) {
            tokens.remove(0);
            return parseDefineSpace(tokens, line, lineNumber, s, source, code);
            
        } else if (token.equalsIgnoreCase(SourceMacro.MACRO_MACRO)) {
            tokens.remove(0);
            return parseMacroDefinition(tokens, line, lineNumber, s, source, code);

        } else if (token.equalsIgnoreCase(SourceMacro.MACRO_ENDM)) {
            config.error(SourceMacro.MACRO_ENDM + " keyword found outside of a macro at " + source.fileName + ", " + 
                         lineNumber + ": " + line);
            return false;

        } else if (config.idiomParser != null && config.idiomParser.recognizeIdiom(tokens)) {
            return config.idiomParser.parseLine(tokens, line, lineNumber, s, source, code);
        } else if (Tokenizer.isSymbol(token)) {
            // try to parse it as an assembler instruction or macro call:
            tokens.remove(0);
            if (config.opParser.isOpName(token)) {
                return parseZ80Op(tokens, token, line, lineNumber, s, source, code);
            } else {
                return parseMacroCall(tokens, token, line, lineNumber, s, source, code);
            }                        
        } else {
            return parseRestofTheLine(tokens, line, lineNumber, s, source);
        }
    }    
    

    public boolean parseRestofTheLine(List<String> tokens, 
            String line, int lineNumber, 
            SourceStatement s, SourceFile source)
    {
        if (tokens.isEmpty()) return true;
        if (tokens.size() == 1 && tokens.get(0).startsWith(";")) {
            s.comment = tokens.get(0);
            return true;
        }
        
        config.error("Cannot parse line " + source.fileName + ", " + 
                     lineNumber + ": " + line);
        return false;
    }
    
    
    public boolean parseOrg(List<String> tokens, 
            String line, int lineNumber,
            SourceStatement s, SourceFile source, CodeBase code)
    {
        Expression exp = config.expressionParser.parse(tokens, code);
        if (exp == null) {
            config.error("Cannot parse line " + source.fileName + ", " + 
                         lineNumber + ": " + line);
            return false;
        } else {
            s.type = SourceStatement.STATEMENT_ORG;
            s.org = exp;
            return parseRestofTheLine(tokens, line, lineNumber, s, source);
        }
    }
    
    
    public boolean parseInclude(List<String> tokens, 
            String line, int lineNumber,
            SourceStatement s, SourceFile source, CodeBase code) throws Exception
    {
        if (tokens.size() >= 1) {
            String token = tokens.get(0);
            if (Tokenizer.isString(token)) {
                tokens.remove(0);
                String rawFileName = Tokenizer.stringValue(token);
                
                // recursive include file:
                String path = resolveIncludePath(rawFileName, source);
                SourceFile includedSource = codeBaseParser.parseSourceFile(path, code, source, s);
                if (includedSource == null) {
                    config.error("Problem including file at " + source.fileName + ", " + 
                                 lineNumber + ": " + line);
                    return false;
                } else {
                    s.type = SourceStatement.STATEMENT_INCLUDE;
                    s.include = includedSource;
                    return parseRestofTheLine(tokens, line, lineNumber, s, source);
                }
            }
        }
        config.error("Cannot parse line " + source.fileName + ", " + 
                     lineNumber + ": " + line);
        return false;
    }
    
    
    public boolean parseIncbin(List<String> tokens, 
            String line, int lineNumber,
            SourceStatement s, SourceFile source, CodeBase code) throws Exception
    {
        if (tokens.size() >= 1) {
            String token = tokens.get(0);
            if (Tokenizer.isString(token)) {
                tokens.remove(0);
                String rawFileName = Tokenizer.stringValue(token);
                String path = resolveIncludePath(rawFileName, source);
                s.type = SourceStatement.STATEMENT_INCBIN;
                s.incbin = path;
                s.incbinOriginalStr = rawFileName;
                File f = new File(path);
                if (!f.exists()) {
                    config.error("Incbin file "+path+" does not exist in " + source.fileName + ", " + 
                                 lineNumber + ": " + line);
                    return false;
                }
                s.incbinSize = (int)f.length();
                return parseRestofTheLine(tokens, line, lineNumber, s, source);
            }
        }
        config.error("Cannot parse line " + source.fileName + ", " + 
                     lineNumber + ": " + line);
        return false;
    }
    
    
    public boolean parseEqu(List<String> tokens, String label, 
            String line, int lineNumber, 
            SourceStatement s, SourceFile source, CodeBase code) throws Exception
    {
        Expression exp = config.expressionParser.parse(tokens, code);
        if (exp == null) {
            config.error("Cannot parse line " + source.fileName + ", " + 
                         lineNumber + ": " + line);
            return false;
        } else {
            Integer value = exp.evaluate(s, code, true);
            
            SourceConstant c = new SourceConstant(labelPrefix+label, value, exp, s);
            s.type = SourceStatement.STATEMENT_CONSTANT;
            s.label = c;
            code.addSymbol(c.name, c);
            return parseRestofTheLine(tokens, line, lineNumber, s, source);
        }
    }
    
    
    public boolean parseData(List<String> tokens, String label, 
            String line, int lineNumber, 
            SourceStatement s, SourceFile source, CodeBase code) throws Exception
    {
        List<Expression> data = new ArrayList<>();
        while(true) {
            Expression exp = config.expressionParser.parse(tokens, code);
            if (exp == null) {
                config.error("Cannot parse line " + source.fileName + ", " + 
                             lineNumber + ": " + line);
                return false;
            } else {
                data.add(exp);
            }
            if (!tokens.isEmpty() && tokens.get(0).equals(",")) {
                tokens.remove(0);
            } else {
                break;
            }
        }
        
        if (label.equalsIgnoreCase("db")) {
            s.type = SourceStatement.STATEMENT_DATA_BYTES;
        } else if (label.equalsIgnoreCase("dw")) {
            s.type = SourceStatement.STATEMENT_DATA_WORDS;
        } else {
            s.type = SourceStatement.STATEMENT_DATA_DOUBLE_WORDS;
        }
        s.data = data;
        
        return parseRestofTheLine(tokens, line, lineNumber, s, source);
    }
    
    
    public boolean parseDefineSpace(List<String> tokens, 
            String line, int lineNumber, 
            SourceStatement s, SourceFile source, CodeBase code) throws Exception
    {
        if (tokens.get(0).equalsIgnoreCase("virtual")) {
            tokens.remove(0);
            Expression exp = config.expressionParser.parse(tokens, code);
            if (exp == null) {
                config.error("Cannot parse line " + source.fileName + ", " + 
                             lineNumber + ": " + line);
                return false;
            }            
            s.type = SourceStatement.STATEMENT_DEFINE_SPACE;
            s.space = exp;
            s.space_value = null;
        } else {
            // In this case, "ds" is just a short-hand for "db" with repeated values:
            Expression exp_amount = config.expressionParser.parse(tokens, code);
            Expression exp_value;
            if (exp_amount == null) {
                config.error("Cannot parse line " + source.fileName + ", " + 
                             lineNumber + ": " + line);
                return false;
            }
            if (!tokens.isEmpty() && !tokens.get(0).startsWith(";")) {
                exp_value = config.expressionParser.parse(tokens, code);
                if (exp_value == null) {
                    config.error("Cannot parse line " + source.fileName + ", " + 
                                 lineNumber + ": " + line);
                    return false;
                }
            } else {
                exp_value = Expression.constantExpression(0);
            }
            
            s.type = SourceStatement.STATEMENT_DEFINE_SPACE;
            s.space = exp_amount;
            s.space_value = exp_value;
        }

        return parseRestofTheLine(tokens, line, lineNumber, s, source);
    }
    
    
    public boolean parseZ80Op(List<String> tokens, String opName,
            String line, int lineNumber, 
            SourceStatement s, SourceFile source, CodeBase code) throws Exception
    {        
        List<Expression> arguments = new ArrayList<>();
        while(!tokens.isEmpty()) {
            if (tokens.get(0).startsWith(";")) break;
            Expression exp = config.expressionParser.parse(tokens, code);
            if (exp == null) {
                config.error("Cannot parse line " + source.fileName + ", " + 
                             lineNumber + ": " + line);
                return false;
            } else {
                arguments.add(exp);
            }
            if (!tokens.isEmpty() && tokens.get(0).equals(",")) {
                tokens.remove(0);
            } else {
                break;
            }
        }
        
        CPUOp op = config.opParser.parseOp(opName, arguments, s, code);        
        if (op == null) {
            config.error("No op spec matches with operator in line " + source.fileName + ", " + 
                         lineNumber + ": " + line);
            return false;
        }
        
        s.type = SourceStatement.STATEMENT_CPUOP;
        s.op = op;
        return parseRestofTheLine(tokens, line, lineNumber, s, source);   
    }
    
    
    public boolean parseMacroDefinition(List<String> tokens, 
            String line, int lineNumber, 
            SourceStatement s, SourceFile source, CodeBase code) throws Exception
    {
        // Marks that all the lines that come after this, and until ENDM,
        // are part of a macro, and should not yet be parsed:
        if (s.label == null) {
            config.error("Cannot parse line " + source.fileName + ", " + 
                         lineNumber + ": " + line);
            return false;            
        }
        
        // parse arguments:
        List<String> args = new ArrayList<>();
        List<Expression> defaultValues = new ArrayList<>();
        while(tokens.size()>=2 && tokens.get(0).equals("?")) {
            tokens.remove(0);
            args.add(tokens.remove(0));
            if (!tokens.isEmpty() && tokens.get(0).equals("=")) {
                // default value:
                tokens.remove(0);
                Expression defaultValue = config.expressionParser.parse(tokens, code);
                if (defaultValue == null) {
                    config.error("Cannot parse default value in line " + source.fileName + ", " + 
                                 lineNumber + ": " + line);
                    return false;            
                }
                defaultValues.add(defaultValue);
            } else {
                defaultValues.add(null);
            }
            if (!tokens.isEmpty() && tokens.get(0).equals(",")) tokens.remove(0);
        }
        
        s.type = SourceStatement.STATEMENT_MACRO;
        s.macroDefinitionArgs = args;
        s.macroDefinitionDefaults = defaultValues;
        return parseRestofTheLine(tokens, line, lineNumber, s, source);   
    }    
    
    
    public boolean parseMacroCall(List<String> tokens, String macroName,
            String line, int lineNumber, 
            SourceStatement s, SourceFile source, CodeBase code) throws Exception
    {        
        List<Expression> arguments = new ArrayList<>();
        while(!tokens.isEmpty()) {
            if (tokens.get(0).startsWith(";")) break;
            Expression exp = config.expressionParser.parse(tokens, code);
            if (exp == null) {
                config.error("Cannot parse line " + source.fileName + ", " + 
                             lineNumber + ": " + line);
                return false;
            } else {
                arguments.add(exp);
            }
            if (!tokens.isEmpty() && tokens.get(0).equals(",")) {
                tokens.remove(0);
            } else {
                break;
            }
        }
        
        s.macroCallName = macroName;
        s.macroCallArguments = arguments;
        s.type = SourceStatement.STATEMENT_MACROCALL;
        return parseRestofTheLine(tokens, line, lineNumber, s, source);
    }    
    
    
    public String resolveIncludePath(String rawFileName, SourceFile source)
    {
        String parentPath = source.getPath();
        String justPath = parentPath;
        String justFileName = rawFileName;
        int idx = rawFileName.lastIndexOf(File.separator);
        if (idx != -1) {
            if (parentPath.equals("")) {
                justPath = rawFileName.substring(0, idx);
            } else {
                justPath = parentPath + File.separator + rawFileName.substring(0, idx);
            }
            justFileName = rawFileName.substring(idx+1);
        }
        String candidatePath = justPath + File.separator + justFileName;
        File f = new File(candidatePath);
        if (f.exists()) return candidatePath;
        
        for(String directory:config.includeDirectories) {
            candidatePath = directory + File.separator + rawFileName;
            f = new File(candidatePath);
            if (f.exists()) return candidatePath;
        }
        
        config.error("Cannot find include file " + rawFileName);
        return null;
    }
    
}
