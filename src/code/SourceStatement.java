/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package code;

import java.util.List;

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
    public SourceFile source;
    public int lineNumber;
    Integer address = null;    // this is just an internal cache of the address
    
    public Expression org;
    public SourceFile include = null;
    public String incbin = null;
    public String incbinOriginalStr = null;
    public int incbinSize = 0;
    public List<Expression> data = null;
    public Expression space = null;
    public Expression space_value = null;   // if this is null, "space" is virtual,
                                            // otherwise, it is filled with this value
    public CPUOp op = null;
    
    public String macroCallName = null;
    public List<Expression> macroCallArguments = null;
    public List<String> macroDefinitionArgs;
    public List<Expression> macroDefinitionDefaults;
    
    // These two are optional attributes that all statements can have:
    public SourceConstant label = null; 
    public String comment = null;
    
    
    public SourceStatement(int a_type, SourceFile a_source, int a_lineNumber, Integer a_address)
    {
        type = a_type;
        source = a_source;
        lineNumber = a_lineNumber;
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
    
    
    public void resetAddress()
    {
        address = null;
    }
    
    
    public Integer getAddress(CodeBase code)
    {
        if (address != null) return address;
        SourceStatement prev = source.getPreviousStatementTo(this, code);
        if (prev == null) {
            address = 0;
        } else {
            address = prev.getAddressAfter(code);
        }
        return address;
    }
    
    
    public Integer getAddressAfter(CodeBase code)
    {
        if (address == null) getAddress(code);
        if (address == null) return null;
        if (type == STATEMENT_ORG) {
            return org.evaluate(this, code, true);
        } else {
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
                    return incbinSize;
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
                    return space.evaluate(this, code, false);
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
                str += "    incbin \"" + incbinOriginalStr + "\"";
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
            case STATEMENT_MACROCALL:
                // we should have resolved all the macros, so, this should not happen
            default:
                return null;
        }
        
        if (comment != null) {
            if (str.isEmpty()) str = comment;
                          else str += "  " + comment; 
        }
        
        return str;
    }
}
