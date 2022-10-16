/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package code;

import cl.MDLConfig;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import parser.SourceLine;
import parser.SourceMacro;

/**
 * A "CodeStatement" can contain zero or one the following plus a comment:
 - constant definition (a label or a constant)
 - an "org" directive
 - an "include" statement
 - an "incbin" statement
 - a macro definition
 - a call to a macro
 - a z80 instruction
 */
public class CodeStatement {
    public static final int STATEMENT_NONE = -1;
    public static final int STATEMENT_ORG = 0;
    public static final int STATEMENT_INCLUDE = 1;
    public static final int STATEMENT_INCBIN = 2;
    public static final int STATEMENT_CONSTANT = 3; // "equ"
    public static final int STATEMENT_DATA_BYTES = 4;
    public static final int STATEMENT_DATA_WORDS = 5;
    public static final int STATEMENT_DATA_DOUBLE_WORDS = 6;
    public static final int STATEMENT_DEFINE_SPACE = 7;
    public static final int STATEMENT_MACRO = 8;
    public static final int STATEMENT_MACROCALL = 9;
    public static final int STATEMENT_CPUOP = 10;
    public static final int STATEMENT_FPOS = 11;
    
    MDLConfig config;
    
    public int type;
    
    public SourceLine sl;
    public SourceFile source;   // Before any optimizations, this should be equivalent to the source obtained
                                // from navigating up the sl.expandedFrom all the way to the parent
    
    public Integer address = null;    // this is just an internal cache of the address
    
    public Expression org;
    public String rawInclude = null;    // name exactly as it appeared in the original statement
    public SourceFile include = null;
    
    public File incbin = null;
    public String incbinOriginalStr = null;
    public boolean incbinSizeSpecified = false;
    public Expression incbinSize = null;
    public Expression incbinSkip = null;
    
    public List<Expression> data = null;
    public Expression space = null;
    public Expression space_value = null;   // if this is null, "space" is virtual,
                                            // otherwise, it is filled with this value
    public CPUOp op = null;
    public Expression fposAbsolute = null, fposOffset = null;
    
    public SourceMacro macroCallMacro = null;   // if we know which macro it is
    public String macroCallName = null;         // if we don't know which macro, just the name
    public List<Expression> macroCallArguments = null;
    public List<String> macroDefinitionArgs;
    public List<Expression> macroDefinitionDefaults;
    
    // These are optional attributes that all statements can have:
    public boolean redefinedLabel = false;
    public SourceConstant label = null; 
    public String comment = null;
    
    // If this statement was created while being inside of some label context (e.g. inside of proc/endp),
    // the context is stored here, in order to resolve labels after code is fully parsed:
    public String labelPrefix = null;
    
    public CodeStatement(int a_type, SourceLine a_sl, SourceFile a_source, MDLConfig a_config)
    {
        type = a_type;
        sl = a_sl;
        source = a_source;
        config = a_config;
    }
    

    public boolean isEmptyAllowingComments()
    {
        if (type == STATEMENT_NONE && label == null) return true;
        return false;
    }
    
    
    public String fileNameLineString()
    {
        return sl.fileNameLineString();
    }
    
    
    public void resetAddress()
    {
        address = null;
    }
    
    
    public Integer getAddress(CodeBase code)
    {
        return getAddressInternal(code, true, null, new ArrayList<>());
    }

    
    public Integer getAddressAfter(CodeBase code)
    {
        return getAddressAfterInternal(code, true, new ArrayList<>());
    }
    

    public Integer getAddress(CodeBase code, CodeStatement previous)
    {
        return getAddressInternal(code, true, previous, new ArrayList<>());
    }

    
    /*
    - previous only needs to be specified if this is called on a CodeStatement not yet added to a source file
      (for example, when parsing a macro being expanded), so that we know which will be the previous statement once it is added
    */
    public Integer getAddressInternal(CodeBase code, boolean recurse, CodeStatement previous, List<String> variableStack)
    {
        if (recurse) {
            if (address != null) return address;
            
            // go back iteratively to prevent a stack overflow:
            List<CodeStatement> trail = new ArrayList<>();
            CodeStatement prev = (previous != null ? previous : source.getPreviousStatementTo(this, code));
            SourceFile prevSource = prev == null ? null : prev.source;
            int prevIdx = prevSource == null ? -1 : prevSource.getStatements().indexOf(prev);
            Integer prevAddressAfter = null;
            while(prev != null) {
                prevAddressAfter = prev.getAddressAfterInternal(code, false, variableStack);
                if (prevAddressAfter != null) {
                    break;
                } else {
                    trail.add(0, prev);
                    if (prevIdx > 0) {
                        prevIdx --;
                        prev = prevSource.getStatements().get(prevIdx);
                    } else {
                        prev = prevSource.getPreviousStatementTo(prev, code);
                        if (prev != null) {
                            prevSource = prev.source;
                            prevIdx = prevSource.getStatements().indexOf(prev);
                        }
                    }
                }                
            }
            
            if (prevAddressAfter == null) {
                // reached beginning of code:
                prevAddressAfter = 0;
            }
            
            // trace forward and update all addresses:
            for(CodeStatement s:trail) {
                s.address = prevAddressAfter;
                if (s.type == STATEMENT_INCLUDE) {
                    prevAddressAfter = s.getAddressAfterInternal(code, true, variableStack);
                } else {
                    prevAddressAfter = s.getAddressAfterInternal(code, false, variableStack);
                }
                if (prevAddressAfter == null) return null;
            }
            address = prevAddressAfter;
            return address;
            
        } else {
            return address;
        }        
    }
    

    Integer getAddressAfterInternal(CodeBase code, boolean recurse, List<String> variableStack)
    {
        switch (type) {
            case STATEMENT_ORG:
                return org.evaluateToIntegerInternal(this, code, true, null, variableStack);
            case STATEMENT_INCLUDE:
                if (include.getStatements().isEmpty()) {
                    if (recurse && address == null) getAddressInternal(code, true, null, variableStack);
                    return address;
                } else {
                    return include.getStatements().get(include.getStatements().size()-1).getAddressAfterInternal(code, recurse, variableStack);
                }
            default:
                if (recurse && address == null) getAddressInternal(code, true, null, variableStack);
                if (address == null) return null;
                Integer size = sizeInBytesInternal(code, true, true, true, variableStack);
                if (size == null) return null;
                return address + size;
        }
    }
    

    public Integer sizeInBytes(CodeBase code, boolean withIncludes, boolean withIncBin, boolean withVirtual)
    {
        return sizeInBytesInternal(code, withIncludes, withIncBin, withVirtual, new ArrayList<>());
    }
    
    
    public Integer sizeInBytesInternal(CodeBase code, boolean withIncludes, boolean withIncBin, boolean withVirtual, List<String> variableStack)
    {
        switch(type) {
            case STATEMENT_INCBIN:
                if (withIncBin) {
                    return incbinSize.evaluateToIntegerInternal(this, code, true, null, variableStack);
                }
                return 0;
            
            case STATEMENT_DATA_BYTES:
            {
                int size = 0;
                for(Expression v:data) {
                    size += v.sizeInBytes(1);
                }
                return size;
            }

            case STATEMENT_DATA_WORDS:
            {
                int size = 0;
                for(Expression v:data) {
                    size += v.sizeInBytes(2);
                }
                return size;
            }

            case STATEMENT_DATA_DOUBLE_WORDS:
            {
                int size = 0;
                for(Expression v:data) {
                    size += v.sizeInBytes(4);
                }
                return size;
            }

            case STATEMENT_DEFINE_SPACE:
                if (withVirtual || space_value != null) {
                    return space.evaluateToIntegerInternal(this, code, true, null, variableStack);
                } else {
                    return 0;
                }

            case STATEMENT_CPUOP:
                return op.sizeInBytes();
                
            case STATEMENT_INCLUDE:
                if (withIncludes) {
                    return include.sizeInBytesInternal(code, withIncludes, withIncBin, withVirtual, variableStack);
                }
                return 0;
                                
            default:
                return 0;
        }
    }
    
    
    public String timeString()
    {
        switch(type) {
            case STATEMENT_CPUOP:
                return op.timeString();
                
            default:
                return "";
        }
    }
        
    
    @Override
    public String toString()
    {
        return toStringUsingRootPath(null, false, false, null, null);
    }


    public String toStringHTML(HTMLCodeStyle style)
    {
        return toStringUsingRootPath(null, false, false, null, style);
    }


    public String toStringHTML(HTMLCodeStyle style, CodeBase code)
    {
        return toStringUsingRootPath(null, false, false, code, style);
    }
    
    
    public String toStringLabel(boolean useOriginalName, boolean useOriginalColonToken, HTMLCodeStyle style)
    {
        String str = "";
        if (label != null) {
            String labelText;
            if (useOriginalName) {
                if (config.output_replaceLabelDotsByUnderscores && !label.originalName.startsWith(".")) {
                    labelText = label.originalName.replace(".", "_");
                } else {
                    labelText = label.originalName;
                }
            } else {
                if (config.output_replaceLabelDotsByUnderscores) {
                    labelText = label.name.replace(".", "_");
                } else {
                    labelText = label.name;
                }
            }
            str += HTMLCodeStyle.renderStyledHTMLPiece(labelText, HTMLCodeStyle.TYPE_LABEL_DEFINITION, style);
            if (type != STATEMENT_CONSTANT || !config.output_equsWithoutColon) {
                if (useOriginalColonToken && label.colonTokenUsedInDefinition != null) {
                    str += label.colonTokenUsedInDefinition;
                } else {
                    str += config.lineParser.KEYWORD_STD_COLON;
                }
            }
            if (type == STATEMENT_NONE && config.output_safetyEquDollar) {
                // check if the next statement is an equ, and generate an additinoal "equ $", since 
                // some assemblers (Glass in particular), interpret this as two labels being defined with
                // the same value, thus misinterpreting the code of other assemblers.
                CodeStatement next = source.getNextStatementTo(this, source.code);
                while(next != null) {
                    if (next.type == STATEMENT_NONE && next.label == null) {
                        next = next.source.getNextStatementTo(next, source.code);
                    } else if (next.type == STATEMENT_INCLUDE && next.label == null) {
                        next = next.include.getNextStatementTo(null, source.code);
                    } else if (next.type == STATEMENT_CONSTANT) {
                        str += " "+config.lineParser.KEYWORD_STD_EQU+" " + CodeBase.CURRENT_ADDRESS;
                        break;
                    } else {
                        break;
                    }
                }
            }
        }
        return str;        
    }
    
            
    public String toStringUsingRootPath(Path rootPath, boolean useOriginalNames, boolean mimicTargetDialect, CodeBase code, HTMLCodeStyle style)
    {
        String str = toStringLabel(useOriginalNames, false, style);
        
        switch(type) {
            case STATEMENT_NONE:
                break;
            case STATEMENT_ORG:
                str += "    "+config.lineParser.KEYWORD_STD_ORG+" " + org.toStringInternal(false, false, false, null, null, style);
                break;
            case STATEMENT_INCLUDE:
            {                
                String path = rawInclude;
                // Make sure we don't have a windows/Unix path separator problem:
                if (path.contains("\\")) {
                    path = path.replace("\\", File.separator);
                }                
                if (style != null) {
                    // We are rendering HTML, generate a link for the include:
                    str += "    "+config.lineParser.KEYWORD_STD_INCLUDE+" <a href=\"#"+include.fileName+"\">\"" + HTMLCodeStyle.renderStyledHTMLPiece(path, HTMLCodeStyle.TYPE_CONSTANT, style) + "\"</a>";
                } else {
                    str += "    "+config.lineParser.KEYWORD_STD_INCLUDE+" \"" + HTMLCodeStyle.renderStyledHTMLPiece(path, HTMLCodeStyle.TYPE_CONSTANT, style) + "\"";
                }
                break;
            }
            case STATEMENT_INCBIN:
            {
                String path = incbinOriginalStr;
                if (rootPath != null && config.relativizeIncbinPaths) {
                    path = rootPath.toAbsolutePath().normalize().relativize(incbin.toPath().toAbsolutePath().normalize()).toString();                    
                }
                // Make sure we don't have a windows/Unix path separator problem:
                if (path.contains("\\")) {
                    path = path.replace("\\", File.separator);
                }                
                if (incbinSkip != null) {
                    if (incbinSizeSpecified) {
                        str += "    "+config.lineParser.KEYWORD_STD_INCBIN+" \"" + HTMLCodeStyle.renderStyledHTMLPiece(path, HTMLCodeStyle.TYPE_CONSTANT, style) + "\", " + incbinSkip + ", " + incbinSize;
                    } else {
                        str += "    "+config.lineParser.KEYWORD_STD_INCBIN+" \"" + HTMLCodeStyle.renderStyledHTMLPiece(path, HTMLCodeStyle.TYPE_CONSTANT, style) + "\", " + incbinSkip;
                    }
                } else {
                    str += "    "+config.lineParser.KEYWORD_STD_INCBIN+" \"" + HTMLCodeStyle.renderStyledHTMLPiece(path, HTMLCodeStyle.TYPE_CONSTANT, style) + "\"";
                }
                break;
            }
            case STATEMENT_CONSTANT:
                if (label == null) {
                    config.error("Trying to write an equ statement without a label in " + sl);
                    return null;
                }
                if (label.exp == null) {
                    config.error("Empty expression when writing an equ statement in " + sl);
                    return null;
                }
                str += " "+config.lineParser.KEYWORD_STD_EQU+" " + label.exp.toStringInternal(false, false, mimicTargetDialect, this, code, style);
                if (style != null && code != null && style.annotateEqusWithFinalValue) {
                    Object value = label.exp.evaluate(this, code, true);
                    if (value != null) {
                        if (value instanceof String) {
                            str += "  ; mdl: " + label.name + " = \"" + value + "\"";
                        } else if (value instanceof Integer) {
                            str += "  ; mdl: " + label.name + " = " + value + " / " + config.tokenizer.toHexWithStyle((Integer)value, 4, config.hexStyle);
                        } else {
                            str += "  ; mdl: " + label.name + " = " + value;
                        }
                    }
                }
                break;
            case STATEMENT_DATA_BYTES:
                str += "    "+config.lineParser.KEYWORD_STD_DB+" ";
                {
                    for(int i = 0;i<data.size();i++) {
                        str += data.get(i).toStringInternal(true, false, mimicTargetDialect, this, code, style);
                        if (i != data.size()-1) {
                            str += ", ";
                        }
                    }
                }
                break;
            case STATEMENT_DATA_WORDS:
                str += "    "+config.lineParser.KEYWORD_STD_DW+" ";
                {
                    for(int i = 0;i<data.size();i++) {
                        str += data.get(i).toStringInternal(false, false, mimicTargetDialect, this, code, style);
                        if (i != data.size()-1) {
                            str += ", ";
                        }
                    }
                }
                break;
            case STATEMENT_DATA_DOUBLE_WORDS:
                str += "    "+config.lineParser.KEYWORD_STD_DD+" ";
                {
                    for(int i = 0;i<data.size();i++) {
                        str += data.get(i).toStringInternal(false, false, mimicTargetDialect, this, code, style);
                        if (i != data.size()-1) {
                            str += ", ";
                        }
                    }
                }
                break;
            case STATEMENT_DEFINE_SPACE:
                if (space_value == null) {
                    if (config.output_allowDSVirtual) {
                        str += "    "+config.lineParser.KEYWORD_STD_DS+" virtual " + space.toStringInternal(false, false, mimicTargetDialect, this, code, style);
                    } else {
                        str += "\n    "+config.lineParser.KEYWORD_STD_ORG+" $ + " + space.toStringInternal(false, false, mimicTargetDialect, this, code, style);
                    }
                } else {
                    if (config.output_replaceDsByData) {
                        int break_each = 16;
                        int space_as_int = space.evaluateToInteger(this, this.source.code, true);
                        String space_str = space_value.toStringInternal(false, false, mimicTargetDialect, this, code, style);
                        str += "    "+config.lineParser.KEYWORD_STD_DB+" ";
                        {
                            for(int i = 0;i<space_as_int;i++) {
                                str += space_str;
                                if (i != space_as_int-1) {
                                    if (((i+1)%break_each) == 0) {
                                        str += "\n    "+config.lineParser.KEYWORD_STD_DB+" ";
                                    } else {
                                        str += ", ";
                                    }
                                }
                            }
                        }
                    } else {
                        str += "    "+config.lineParser.KEYWORD_STD_DS+" " + space.toStringInternal(false, false, mimicTargetDialect, this, code, style) + ", " + space_value;
                    }
                }
                break;
                
            case STATEMENT_CPUOP:
                str += "    " + op.toStringInternal(useOriginalNames, mimicTargetDialect, this, code, style);
                break;
                
            case STATEMENT_MACRO:
                // we should have resolved all the macros, so, this should not happen though
                return null;
            case STATEMENT_MACROCALL:
            {
                if (macroCallMacro != null) {
                    
                    str += "    " + HTMLCodeStyle.renderStyledHTMLPiece(macroCallMacro.name, HTMLCodeStyle.TYPE_MACRO, style) + " ";
                } else {
                    str += "    " + HTMLCodeStyle.renderStyledHTMLPiece(macroCallName, HTMLCodeStyle.TYPE_MACRO, style) + " ";
                }
                for(int i = 0;i<macroCallArguments.size();i++) {
                    if (i==0) {
                        str += macroCallArguments.get(i).toStringInternal(false, false, mimicTargetDialect, this, code, style);
                    } else {
                        str += ", " + macroCallArguments.get(i).toStringInternal(false, false, mimicTargetDialect, this, code, style);
                    }
                }
                return str;
            }
            case STATEMENT_FPOS:
            {
                if (fposAbsolute != null) {
                    str += "    " + config.lineParser.KEYWORD_STD_FPOS + " " + fposAbsolute.toStringInternal(false, false, mimicTargetDialect, this, code, style);
                } else {
                    str += "    " + config.lineParser.KEYWORD_STD_FPOS + " " + fposOffset.toStringInternal(false, false, mimicTargetDialect, this, code, style);
                }
                break;
            }
            default:
                return null;
        }
        
        if (comment != null) {
            String actualComment = comment;
            if (!comment.startsWith(";") && !mimicTargetDialect) {
                actualComment = "; " + comment;
            }
            if (str.isEmpty()) str = HTMLCodeStyle.renderStyledHTMLPiece(actualComment, HTMLCodeStyle.TYPE_COMMENT, style);
                          else str += "  " + HTMLCodeStyle.renderStyledHTMLPiece(actualComment, HTMLCodeStyle.TYPE_COMMENT, style); 
        }
        
        return str;
    }
    
    
    public void evaluateAllExpressions(CodeBase code, MDLConfig config)
    {
        if (org != null && org.evaluatesToIntegerConstant()) {
            org = Expression.constantExpression(org.evaluateToInteger(this, code, false), config);
        } 
        if (incbinSize != null && incbinSize.evaluatesToIntegerConstant()) {
            incbinSize = Expression.constantExpression(incbinSize.evaluateToInteger(this, code, false), config);
        } 
        if (incbinSkip != null && incbinSkip.evaluatesToIntegerConstant()) {
            incbinSkip = Expression.constantExpression(incbinSkip.evaluateToInteger(this, code, false), config);
        } 
        if (data != null) {
            for(int i = 0;i<data.size();i++) {
                if (data.get(i).evaluatesToIntegerConstant()) {
                    data.set(i, Expression.constantExpression(data.get(i).evaluateToInteger(this, code, false), config));
                }
            }
        }
        if (space != null && space.evaluatesToIntegerConstant()) {
            space = Expression.constantExpression(space.evaluateToInteger(this, code, false), config);
        } 
        if (space_value != null && space_value.evaluatesToIntegerConstant()) {
            space_value = Expression.constantExpression(space_value.evaluateToInteger(this, code, false), config);
        } 
        if (fposAbsolute != null && fposAbsolute.evaluatesToIntegerConstant()) {
            fposAbsolute = Expression.constantExpression(fposAbsolute.evaluateToInteger(this, code, false), config);
        } 
        if (fposOffset != null && fposOffset.evaluatesToIntegerConstant()) {
            fposOffset = Expression.constantExpression(fposOffset.evaluateToInteger(this, code, false), config);
        } 
        if (op != null) op.evaluateAllExpressions(this, code, config);
        if (macroCallArguments != null) {
            for(int i = 0;i<macroCallArguments.size();i++) {
                if (macroCallArguments.get(i).evaluatesToIntegerConstant()) {
                    macroCallArguments.set(i, Expression.constantExpression(macroCallArguments.get(i).evaluateToInteger(this, code, false), config));
                }
            }
        }
        if (macroDefinitionDefaults != null) {
            for(int i = 0;i<macroDefinitionDefaults.size();i++) {
                if (macroDefinitionDefaults.get(i).evaluatesToIntegerConstant()) {
                    macroDefinitionDefaults.set(i, Expression.constantExpression(macroDefinitionDefaults.get(i).evaluateToInteger(this, code, false), config));
                }
            }
        }        
    }
    
    
    public void resolveLocalLabels(CodeBase code)
    {
        if (labelPrefix == null || labelPrefix.isEmpty()) return;
        
        if (label != null) {
            if (label.exp != null) {
                label.exp.resolveLocalLabels(labelPrefix, this, code);
            }
        }
        if (org != null) org.resolveLocalLabels(labelPrefix, this, code);
        if (incbinSize != null) incbinSize.resolveLocalLabels(labelPrefix, this, code);
        if (incbinSkip != null) incbinSkip.resolveLocalLabels(labelPrefix, this, code);
        if (space != null) space.resolveLocalLabels(labelPrefix, this, code);
        if (space_value != null) space_value.resolveLocalLabels(labelPrefix, this, code);
        if (fposAbsolute != null) fposAbsolute.resolveLocalLabels(labelPrefix, this, code);
        if (fposOffset != null) fposOffset.resolveLocalLabels(labelPrefix, this, code);
        if (data != null) {
            for(Expression exp:data) {
                exp.resolveLocalLabels(labelPrefix, this, code);
            }
        }
        if (op != null) {
            for(Expression exp:op.args) {
                exp.resolveLocalLabels(labelPrefix, this, code);
            }
        }
        if (macroCallArguments != null) {
            for(Expression exp:macroCallArguments) {
                exp.resolveLocalLabels(labelPrefix, this, code);
            }            
        }
    }
    
    
    public List<Expression> getAllExpressions()
    {
        List<Expression> l = new ArrayList<>();
        
        if (org != null) l.add(org);
        if (incbinSize != null) l.add(incbinSize);
        if (incbinSkip != null) l.add(incbinSkip);
        if (space != null) l.add(space);
        if (space_value != null) l.add(space_value);
        if (fposAbsolute != null) l.add(fposAbsolute);
        if (fposOffset != null) l.add(fposOffset);
        if (data != null) l.addAll(data);
        if (op != null) l.addAll(op.args);
        if (macroCallArguments != null) l.addAll(macroCallArguments);
        if (label != null && label.exp != null) l.add(label.exp);
        return l;
    }
    
    
    public SourceConstant getLastAbsoluteLabel() 
    {
        CodeStatement s = this;
        while(s != null) {
            if (s.label != null && s.label.isLabel() && 
                s.label.relativeTo == null) {
                return s.label;
            } else {
                s = s.source.getPreviousStatementTo(s, s.source.code);
            }
        }
        return null;        
    }
    
    
    // Each MDLConfig encapsulates a given dialect. Since the optimization patterns are
    // written in some default dialect, we might neeed to switch the config from one to
    // the other at some point:
    public void setConfig(MDLConfig newConfig)
    {
        config = newConfig;
        if (org != null) org.setConfig(newConfig);
        if (incbinSize != null) incbinSize.setConfig(newConfig);
        if (incbinSkip != null) incbinSkip.setConfig(newConfig);
        if (data != null) {
            for(Expression v:data) {
                v.setConfig(newConfig);
            }
        }
        if (space != null) space.setConfig(newConfig);
        if (space_value != null) space_value.setConfig(newConfig);
        if (fposAbsolute != null) fposAbsolute.setConfig(newConfig);
        if (fposOffset != null) fposOffset.setConfig(newConfig);
        if (op != null) op.setConfig(newConfig);
        if (macroCallArguments != null) {
            for(Expression v:macroCallArguments) {
                if (v != null) v.setConfig(newConfig);
            }
        }
        if (macroDefinitionDefaults != null) {
            for(Expression v:macroDefinitionDefaults) {
                if (v != null) v.setConfig(newConfig);
            }
        }
        if (label != null) label.setConfig(newConfig);    
    }
}
