/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package code;

import cl.MDLConfig;
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
    
    public String incbin = null;
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
    
    
    public SourceStatement(int a_type, SourceLine a_sl, SourceFile a_source, Integer a_address)
    {
        type = a_type;
        sl = a_sl;
        source = a_source;
        address = a_address;
    }
    
    
    public boolean isEmpty()
    {
        if (type == STATEMENT_NONE && comment == null && label == null) return true;
        return false;
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
            while(prev != null) {
                if (prev.getAddressAfterInternal(code, false) != null) {
                    break;
                } else {
                    trail.add(0, prev);
                    prev = prev.source.getPreviousStatementTo(prev, code);
                }
            }
            // now it should be possible to do it:
            for(SourceStatement s:trail) {
                if (s.getAddress(code) == null) return null;
            }

            prev = source.getPreviousStatementTo(this, code);
            if (prev == null) {
                address = 0;
            } else {
                address = prev.getAddressAfter(code);
            }
            return address;
            
        } else {
            return address;
        }        
    }
    
    
    public Integer getAddressAfter(CodeBase code)
    {
        switch (type) {
            case STATEMENT_ORG:
                return org.evaluate(this, code, true);
            case STATEMENT_INCLUDE:
                return include.getStatements().get(include.getStatements().size()-1).getAddressAfter(code);
            default:
                if (address == null) getAddress(code);
                if (address == null) return null;
                Integer size = sizeInBytes(code, true, true, true);
                if (size == null) return null;
                return address + size;
        }
    }


    public Integer getAddressAfterInternal(CodeBase code, boolean recurse)
    {
        switch (type) {
            case STATEMENT_ORG:
                return org.evaluate(this, code, true);
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
                    return incbinSize.evaluate(this, code, true);
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
                    return space.evaluate(this, code, true);
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
        String str = "";
        if (label != null) str = label.name + ":";
        
        
        switch(type) {
            case STATEMENT_NONE:
                break;
            case STATEMENT_ORG:
                str += "    org " + org.toString();
                break;
            case STATEMENT_INCLUDE:
                str += "    include \"" + include.fileName + "\"";
                break;
            case STATEMENT_INCBIN:
                if (incbinSkip != null) {
                    if (incbinSizeSpecified) {
                        str += "    incbin \"" + incbinOriginalStr + "\", " + incbinSkip + ", " + incbinSize;
                    } else {
                        str += "    incbin \"" + incbinOriginalStr + "\", " + incbinSkip;
                    }
                } else {
                    str += "    incbin \"" + incbinOriginalStr + "\"";
                }
                break;
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
        if (org != null && org.evaluatesToNumericConstant()) {
            org = Expression.constantExpression(org.evaluate(this, code, false), config);
        } 
        if (incbinSize != null && incbinSize.evaluatesToNumericConstant()) {
            incbinSize = Expression.constantExpression(incbinSize.evaluate(this, code, false), config);
        } 
        if (incbinSkip != null && incbinSkip.evaluatesToNumericConstant()) {
            incbinSkip = Expression.constantExpression(incbinSkip.evaluate(this, code, false), config);
        } 
        if (data != null) {
            for(int i = 0;i<data.size();i++) {
                if (data.get(i).evaluatesToNumericConstant()) {
                    data.set(i, Expression.constantExpression(data.get(i).evaluate(this, code, false), config));
                }
            }
        }
        if (space != null && space.evaluatesToNumericConstant()) {
            space = Expression.constantExpression(space.evaluate(this, code, false), config);
        } 
        if (space_value != null && space_value.evaluatesToNumericConstant()) {
            space_value = Expression.constantExpression(space_value.evaluate(this, code, false), config);
        } 
        if (op != null) op.evaluateAllExpressions(this, code, config);
        if (macroCallArguments != null) {
            for(int i = 0;i<macroCallArguments.size();i++) {
                if (macroCallArguments.get(i).evaluatesToNumericConstant()) {
                    macroCallArguments.set(i, Expression.constantExpression(macroCallArguments.get(i).evaluate(this, code, false), config));
                }
            }
        }
        if (macroDefinitionDefaults != null) {
            for(int i = 0;i<macroDefinitionDefaults.size();i++) {
                if (macroDefinitionDefaults.get(i).evaluatesToNumericConstant()) {
                    macroDefinitionDefaults.set(i, Expression.constantExpression(macroDefinitionDefaults.get(i).evaluate(this, code, false), config));
                }
            }
        }        
    }
}
