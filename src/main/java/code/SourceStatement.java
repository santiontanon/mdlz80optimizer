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
 * A "SourceStatement" can contain zero or one the following plus a comment:
 * - constant definition (a label or a constant)
 * - an "org" directive
 * - an "include" statement
 * - an "incbin" statement
 * - a macro definition
 * - a call to a macro
 * - a z80 instruction
 */
public class SourceStatement {
    public static final int STATEMENT_NONE = -1;
    public static final int STATEMENT_ORG = 0;
    public static final int STATEMENT_INCLUDE = 1;
    public static final int STATEMENT_INCBIN = 2;
    public static final int STATEMENT_CONSTANT = 3; // source labels are considered
                                                    // constants, with value == "$"
    public static final int STATEMENT_DATA_BYTES = 4;
    public static final int STATEMENT_DATA_WORDS = 5;
    public static final int STATEMENT_DATA_DOUBLE_WORDS = 6;
    public static final int STATEMENT_DEFINE_SPACE = 7;
    public static final int STATEMENT_MACRO = 8;
    public static final int STATEMENT_MACROCALL = 9;
    public static final int STATEMENT_CPUOP = 10;
    
    
    public int type;
    
    public SourceLine sl;
    public SourceFile source;   // this should be equivalent to the source obtained
                                // from navigating up the sl.expandedFrom all the way to the parent
    
    Integer address = null;    // this is just an internal cache of the address
    
    public Expression org;
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
    
    public SourceMacro macroCallMacro = null;   // if we know which macro it is
    public String macroCallName = null;         // if we don't know which macro, just the name
    public List<Expression> macroCallArguments = null;
    public List<String> macroDefinitionArgs;
    public List<Expression> macroDefinitionDefaults;
    
    // These two are optional attributes that all statements can have:
    public SourceConstant label = null; 
    public String comment = null;
    
    // If this statement was created while being inside of some label context (e.g. inside of proc/endp),
    // the context is stored here, in order to resolve labels after code is fully parsed:
    public String labelPrefix = null;
    
    public SourceStatement(int a_type, SourceLine a_sl, SourceFile a_source)
    {
        type = a_type;
        sl = a_sl;
        source = a_source;
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
        return getAddressInternal(code, true);
    }


    public Integer getAddressInternal(CodeBase code, boolean recurse)
    {

        if (recurse) {
            if (address != null) return address;
            
            // go back iteratively to prevent a stack overflow:
            List<SourceStatement> trail = new ArrayList<>();
            SourceStatement prev = source.getPreviousStatementTo(this, code);
            SourceFile prevSource = prev == null ? null : prev.source;
            int prevIdx = prevSource == null ? -1 : prevSource.getStatements().indexOf(prev);
            Integer prevAddressAfter = null;
            while(prev != null) {
                prevAddressAfter = prev.getAddressAfterInternal(code, false);
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
            for(SourceStatement s:trail) {
                s.address = prevAddressAfter;
                if (s.type == STATEMENT_INCLUDE) {
                    prevAddressAfter = s.getAddressAfterInternal(code, true);
                } else {
                    prevAddressAfter = s.getAddressAfterInternal(code, false);
                }
                if (prevAddressAfter == null) return null;
            }
            address = prevAddressAfter;
            return address;
            
        } else {
            return address;
        }        
    }
    

    Integer getAddressAfterInternal(CodeBase code, boolean recurse)
    {
        switch (type) {
            case STATEMENT_ORG:
                return org.evaluateToInteger(this, code, true);
            case STATEMENT_INCLUDE:
                return include.getStatements().get(include.getStatements().size()-1).getAddressAfterInternal(code, recurse);
            default:
                if (recurse && address == null) getAddress(code);
                if (address == null) return null;
                Integer size = sizeInBytes(code, true, true, true);
                if (size == null) return null;
                return address + size;
        }
    }
    
    
    public Integer sizeInBytes(CodeBase code, boolean withIncludes, boolean withIncBin, boolean withVirtual)
    {
        switch(type) {
            case STATEMENT_INCBIN:
                if (withIncBin) {
                    return incbinSize.evaluateToInteger(this, code, true);
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
                    return space.evaluateToInteger(this, code, true);
                } else {
                    return 0;
                }

            case STATEMENT_CPUOP:
                return op.sizeInBytes();
                
            case STATEMENT_INCLUDE:
                if (withIncludes) {
                    return include.sizeInBytes(code, withIncludes, withIncBin, withVirtual);
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
        return toStringUsingRootPath(null);
    }


    public String toStringUsingRootPath(Path rootPath)
    {
        String str = "";
        if (label != null) {
            str = label.name + ":";
            if (type == STATEMENT_NONE) {
                // check if the next statement is an equ, and generate an additinoal "equ $", since 
                // some assemblers (Glass in particular), interpret this as two labels being defined with
                // the same value, thus misinterpreting the code of other assemblers.
                SourceStatement next = source.getNextStatementTo(this, source.code);
                while(next != null) {
                    if (next.type == STATEMENT_NONE && next.label == null) {
                        next = next.source.getNextStatementTo(next, source.code);
                    } else if (next.type == STATEMENT_INCLUDE && next.label == null) {
                        next = next.include.getNextStatementTo(null, source.code);
                    } else if (next.type == STATEMENT_CONSTANT) {
                        str += " equ " + CodeBase.CURRENT_ADDRESS;
                        break;
                    } else {
                        break;
                    }
                }
            }
        }
        
        switch(type) {
            case STATEMENT_NONE:
                break;
            case STATEMENT_ORG:
                str += "    org " + org.toString();
                break;
            case STATEMENT_INCLUDE:
            {
                String path = include.fileName;
                // Make sure we don't have a windows/Unix path separator problem:
                if (path.contains("\\")) {
                    path = path.replace("\\", File.separator);
                }                
                str += "    include \"" + path + "\"";
                break;
            }
            case STATEMENT_INCBIN:
            {
                String path = incbinOriginalStr;
                if (rootPath != null) {
                    path = rootPath.toAbsolutePath().normalize().relativize(incbin.toPath().toAbsolutePath().normalize()).toString();
                }
                // Make sure we don't have a windows/Unix path separator problem:
                if (path.contains("\\")) {
                    path = path.replace("\\", File.separator);
                }                
                if (incbinSkip != null) {
                    if (incbinSizeSpecified) {
                        str += "    incbin \"" + path + "\", " + incbinSkip + ", " + incbinSize;
                    } else {
                        str += "    incbin \"" + path + "\", " + incbinSkip;
                    }
                } else {
                    str += "    incbin \"" + path + "\"";
                }
                break;
            }
            case STATEMENT_CONSTANT:
                str += " equ " + label.exp.toString();
                break;
            case STATEMENT_DATA_BYTES:
                str += "    db ";
                {
                    for(int i = 0;i<data.size();i++) {
                        str += data.get(i).toString();
                        if (i != data.size()-1) {
                            str += ", ";
                        }
                    }
                }
                break;
            case STATEMENT_DATA_WORDS:
                str += "    dw ";
                {
                    for(int i = 0;i<data.size();i++) {
                        str += data.get(i).toString();
                        if (i != data.size()-1) {
                            str += ", ";
                        }
                    }
                }
                break;
            case STATEMENT_DATA_DOUBLE_WORDS:
                str += "    dd ";
                {
                    for(int i = 0;i<data.size();i++) {
                        str += data.get(i).toString();
                        if (i != data.size()-1) {
                            str += ", ";
                        }
                    }
                }
                break;
            case STATEMENT_DEFINE_SPACE:
                if (space_value == null) {
                    str += "    ds virtual " + space;
                } else {
                    str += "    ds " + space + ", " + space_value;
                }
                break;
                
            case STATEMENT_CPUOP:
                str += "    " + op.toString();
                break;
                
            case STATEMENT_MACRO:
                // we should have resolved all the macros, so, this should not happen though
                return null;
            case STATEMENT_MACROCALL:
            {
                if (macroCallMacro != null) {
                    str += "    " + macroCallMacro.name + " ";
                } else {
                    str += "    " + macroCallName + " ";
                }
                for(int i = 0;i<macroCallArguments.size();i++) {
                    if (i==0) {
                        str += macroCallArguments.get(i);
                    } else {
                        str += ", " + macroCallArguments.get(i);
                    }
                }
                return str;
            }
            default:
                return null;
        }
        
        if (comment != null) {
            if (str.isEmpty()) str = comment;
                          else str += "  " + comment; 
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
}
