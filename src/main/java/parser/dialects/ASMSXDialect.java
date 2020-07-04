/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package parser.dialects;

import java.util.ArrayList;
import java.util.List;

import cl.MDLConfig;
import cl.MDLLogger;
import code.CodeBase;
import code.Expression;
import code.SourceConstant;
import code.SourceFile;
import code.SourceStatement;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import parser.SourceMacro;
import parser.Tokenizer;
import util.Resources;

/**
 *
 * @author santi
 */
public class ASMSXDialect implements Dialect {
    MDLConfig config;
    
    String biosCallsFileName = "data/msx-bios-calls.asm";    

    String lastAbsoluteLabel = null;
    SourceStatement romHeaderStatement = null;
    Expression startLabel = null;


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
    }


    @Override
    public boolean recognizeIdiom(List<String> tokens) {
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".bios")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".filename")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".size")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".page")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".printtext")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".print")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".printdec")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".printhex")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".byte")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".word")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".rom")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".start")) return true;
        return false;
    }


    @Override
    public String newSymbolName(String name, Expression value) {
        // TODO(santi@): complete this list (or maybe have a better way than an if-then rule list!
        if (name.equalsIgnoreCase(".bios") ||
            name.equalsIgnoreCase(".filename") ||
            name.equalsIgnoreCase(".size") ||
            name.equalsIgnoreCase(".page") ||
            name.equalsIgnoreCase(".printtext") ||
            name.equalsIgnoreCase(".print") ||
            name.equalsIgnoreCase(".printdec") ||
            name.equalsIgnoreCase(".printhex") ||
            name.equalsIgnoreCase(".byte") ||
            name.equalsIgnoreCase(".word") ||
            name.equalsIgnoreCase(".rom") ||
            name.equalsIgnoreCase(".start")) {
            return null;
        }
        if (name.startsWith("@@")) {
            return lastAbsoluteLabel + "." + name.substring(2);
        } else if (name.startsWith(".")) {
            return lastAbsoluteLabel + "." + name.substring(1);
        } else {
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
                    .filter(line -> !line.startsWith(";"))
                    .map(line -> parseBiosCallLine(line))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            config.error("Cannot read bios calls file "+biosCallsFileName);
            return null;
        }
    }
    

    @Override
    public boolean parseLine(List<String> tokens, String line, int lineNumber, SourceStatement s, SourceFile source, CodeBase code) {
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".bios")) {
            tokens.remove(0);
            // Define all the bios calls:
            List<String []> biosCalls = loadBiosCalls();
            if (biosCalls == null) {
                config.error("Cannot read bios calls file in " + source.fileName + ", " + lineNumber + ": " + line);
                return false;                
            }
            for(String []biosCall:biosCalls) {
                SourceStatement biosCallStatement = new SourceStatement(SourceStatement.STATEMENT_CONSTANT, source, lineNumber, null);
                int address = Tokenizer.parseHex(biosCall[1]);
                biosCallStatement.label = new SourceConstant(biosCall[0], address, Expression.constantExpression(address, config), biosCallStatement);
                source.addStatement(biosCallStatement);
            }
            return config.lineParser.parseRestofTheLine(tokens, line, lineNumber, s, source);
        }
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".filename")) {
            tokens.remove(0);
            tokens.remove(0);
            // just ignore this line
            return config.lineParser.parseRestofTheLine(tokens, line, lineNumber, s, source);
        }
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".size")) {
            tokens.remove(0);
            Expression size_exp = config.expressionParser.parse(tokens, code);
            if (size_exp == null) {
                MDLLogger.logger().error("Cannot parse .size parameter in {}, {}: {}", source.fileName, lineNumber, line);
                return false;
            }
            // Since we are not producing compiled output, we ignore this directive
            return config.lineParser.parseRestofTheLine(tokens, line, lineNumber, s, source);
        }
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".page")) {
            tokens.remove(0);
            Expression page_exp = config.expressionParser.parse(tokens, code);
            if (page_exp == null) {
                MDLLogger.logger().error("Cannot parse .page parameter in {}, {}: {}", source.fileName, lineNumber, line);
                return false;
            }
            s.type = SourceStatement.STATEMENT_ORG;
            s.org = Expression.operatorExpression(Expression.EXPRESSION_MUL,
                    Expression.parenthesisExpression(page_exp, config),
                    Expression.constantExpression(16*1024, config), config);
            // Since we are not producing compiled output, we ignore this directive
            return config.lineParser.parseRestofTheLine(tokens, line, lineNumber, s, source);
        }
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".printtext")) {
            MDLLogger.logger().info(tokens.get(1));
            tokens.remove(0);
            tokens.remove(0);
            return config.lineParser.parseRestofTheLine(tokens, line, lineNumber, s, source);
        }
        if (tokens.size()>=2 && (tokens.get(0).equalsIgnoreCase(".printdec") || tokens.get(0).equalsIgnoreCase(".print"))) {
            tokens.remove(0);
            Expression exp = config.expressionParser.parse(tokens, code);
            Integer value = exp.evaluate(s, code, true);
            if (value == null) {
                MDLLogger.logger().error("Cannot evaluate expression in {}, {}: {}", source.fileName, lineNumber, line);
                return false;
            }
            MDLLogger.logger().debug(".printdec: {}", value);
            return config.lineParser.parseRestofTheLine(tokens, line, lineNumber, s, source);
        }
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".printhex")) {
            tokens.remove(0);
            Expression exp = config.expressionParser.parse(tokens, code);
            Integer value = exp.evaluate(s, code, true);
            if (value == null) {
                MDLLogger.logger().error("Cannot evaluate expression in {}, {}: {}", source.fileName, lineNumber, line);
                return false;
            }
            MDLLogger.logger().debug(".printhex: {}", Tokenizer.toHexWord(value, config.hexStyle));
            return config.lineParser.parseRestofTheLine(tokens, line, lineNumber, s, source);
        }
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".byte")) {
            tokens.remove(0);
            s.type = SourceStatement.STATEMENT_DEFINE_SPACE;
            s.space = Expression.constantExpression(1, config);
            s.space_value = null;
            return config.lineParser.parseRestofTheLine(tokens, line, lineNumber, s, source);
        }
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".word")) {
            tokens.remove(0);
            s.type = SourceStatement.STATEMENT_DEFINE_SPACE;
            s.space = Expression.constantExpression(2, config);
            s.space_value = null;
            return config.lineParser.parseRestofTheLine(tokens, line, lineNumber, s, source);
        }
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".rom")) {
            tokens.remove(0);
            // Generates a ROM header (and stores a pointer to the start address, to later modify with the .start directive):
            List<Expression> data = new ArrayList<>();
            romHeaderStatement = s;
            s.type = SourceStatement.STATEMENT_DATA_BYTES;
            s.data = data;
            data.add(Expression.constantExpression("AB", config));
            if (startLabel == null) {
                data.add(Expression.constantExpression(0, config)); // start address place holder
                data.add(Expression.constantExpression(0, config)); 
            } else {
                data.add(Expression.operatorExpression(Expression.EXPRESSION_MOD, 
                        startLabel, 
                        Expression.constantExpression(256, config), config)); 
                data.add(Expression.operatorExpression(Expression.EXPRESSION_DIV, 
                        startLabel, 
                        Expression.constantExpression(256, config), config)); 
            }
            for(int i = 0;i<12;i++) data.add(Expression.constantExpression(0, config));
            return config.lineParser.parseRestofTheLine(tokens, line, lineNumber, s, source);
        }
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".start")) {
            tokens.remove(0);
            Expression exp = config.expressionParser.parse(tokens, code);
            if (exp == null) {
                MDLLogger.logger().error("Cannot parse expression in {}, {}: {}", source.fileName, lineNumber, line);
                return false;
            }
            startLabel = exp;
            if (romHeaderStatement != null) {
                romHeaderStatement.data.set(1,Expression.operatorExpression(Expression.EXPRESSION_MOD, 
                        startLabel, 
                        Expression.constantExpression(256, config), config)); 
                romHeaderStatement.data.set(2,Expression.operatorExpression(Expression.EXPRESSION_DIV, 
                        startLabel, 
                        Expression.constantExpression(256, config), config)); 
            }
            return config.lineParser.parseRestofTheLine(tokens, line, lineNumber, s, source);
        }

        MDLLogger.logger().error("ASMSXDialect cannot parse line in {}, {}: {}", source.fileName, lineNumber, line);
        return false;
    }


    @Override
    public boolean newMacro(SourceMacro macro, CodeBase code) {
        return true;
    }
    

}
