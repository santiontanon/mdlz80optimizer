/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package code;

import cl.MDLConfig;
import java.util.ArrayList;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;
import parser.SourceLine;

public class CodeBase {    
    public static final String CURRENT_ADDRESS = "$";

    MDLConfig config;

    LinkedHashMap<String, SourceFile> sources = new LinkedHashMap<>();
    LinkedHashMap<String, SourceConstant> symbols = new LinkedHashMap<>();
    
    // Stores the mdl:no-opt-start / mdl:no-opt-end protected blocks of code:
    public List<Pair<SourceLine, SourceLine>> optimizationProtectedBlocks = new ArrayList<>();

    // List of the expected output binaries:
    public List<OutputBinary> outputs = new ArrayList<>();


    public CodeBase(MDLConfig a_config)
    {
        config = a_config;
    }


    public boolean isRegister(String name)
    {
        String registers[] = {"a", "b", "c", "d", "e", "h","l",
                              "af", "bc", "de", "hl",
                              "sp", "ix", "iy", "pc",
                              "ixl", "ixh", "iyl", "iyh",
                              "af'",
                              "i", "r"};
        for(String reg:registers) {
            if (name.equalsIgnoreCase(reg)) return true;
        }

        return false;
    }

    
    public boolean isRegisterPair(String name)
    {
        String registers[] = {"af", "bc", "de", "hl",
                              "sp", "ix", "iy", "pc","af'"};
        for(String reg:registers) {
            if (name.equalsIgnoreCase(reg)) return true;
        }

        return false;
    }
    

    public boolean isCondition(String name)
    {
        String conditions[] = {"c", "m", "nc", "nz", "p", "pe", "po", "z"};
        for(String c:conditions) {
            if (name.equalsIgnoreCase(c)) return true;
        }

        return false;
    }


    public SourceConstant getSymbol(String name)
    {
        if (symbols.containsKey(name)) {
            return symbols.get(name);
        }
        return null;
    }


    public Set<String> getSymbols()
    {
        return symbols.keySet();
    }


    public Object getSymbolValue(String name, boolean silent)
    {
        if (symbols.containsKey(name)) {
            return symbols.get(name).getValue(this, silent);
        }
        return null;
    }
    

    public Object getSymbolValueInternal(String name, boolean silent, List<String> variableStack)
    {
        if (symbols.containsKey(name)) {
            return symbols.get(name).getValueInternal(this, silent, null, variableStack);
        }
        return null;
    }

    
    public void removeSymbol(String name)
    {
        symbols.remove(name);
    }
    

    // Returns:
    // 1: ok
    // 0: redefinition
    // -1: error
    public int addSymbol(String name, SourceConstant sc)
    {
        if (symbols.containsKey(name)) {
            SourceConstant previous = symbols.get(name);
            if (previous.resolveEagerly) {
                if (sc.exp != null) {
                    // resolve it right away, before replacing:
                    Object value = sc.exp.evaluate(sc.definingStatement, this, false);
                    if (value == null) {
                        config.error("Cannot resolve eager variable in " + sc.definingStatement.sl);
                        sc.exp.evaluate(sc.definingStatement, this, false);
                        return -1;
                    }
                    if (value instanceof Integer) {
                        sc.exp = Expression.constantExpression((Integer)value, config);
                    } else if (value instanceof Double) {
                        sc.exp = Expression.constantExpression((Double)value, config);
                    } else if (value instanceof String) {
                        sc.exp = Expression.constantExpression((String)value, config);
                    } else {
                        config.error("Cannot resolve eager variable in " + sc.definingStatement.sl);
                        return -1;
                    }
                } else {
                    sc.exp = previous.exp;
                }
            } else {
                if (symbols.get(name).exp != null) {
                    config.warn("Redefining symbol " + name);
                    config.warn("First defined in " + symbols.get(name).definingStatement.sl.source.fileName + ", " + symbols.get(name).definingStatement.sl.lineNumber + " as " + symbols.get(name).exp + ": " +  symbols.get(name).definingStatement);
                    config.warn("Redefined in " + sc.definingStatement.sl);
                    sc.exp = previous.exp;
                    return 0;
                }
            }
        }
        symbols.put(name, sc);
        return 1;
    }

    
    public CodeStatement statementDefiningLabel(String name)
    {
        SourceConstant sc = getSymbol(name);
        if (sc == null) return null;
        return sc.definingStatement;
//        for(SourceFile f:sources.values()) {
//            for(CodeStatement s:f.getStatements()) {
//                if (s.label != null && s.label.name.equals(name)) return s;
//            }
//        }
//        return null;
    }
    

    public Collection<SourceFile> getSourceFiles()
    {
        return sources.values();
    }


    public SourceFile getSourceFile(String fileName)
    {
        if (sources.containsKey(fileName)) return sources.get(fileName);
        return null;
    }


    public void addSourceFile(SourceFile s)
    {
        sources.put(s.fileName, s);
    }


    public void resetAddresses()
    {
        for(SourceFile f:sources.values()) {
            f.resetAddresses();
        }
        for(SourceConstant c:symbols.values()) {
            c.valueCache = null;
        }
    }

    
    public void addOutput(String binaryName, SourceFile main)
    {
        OutputBinary output = new OutputBinary(binaryName, main);
        outputs.add(output);
    }


    public void evaluateAllExpressions()
    {
        for(SourceFile f:sources.values()) {
            f.evaluateAllExpressions(this);
        }
    }
    
    
    public boolean protectedFromOptimization(CodeStatement s) 
    {
        if (s.comment != null && s.comment.contains(config.PRAGMA_NO_OPTIMIZATION)) return true;
        return protectedFromOptimization(s.sl);
    }


    public boolean protectedFromOptimization(SourceLine sl) 
    {
        if (sl.line.contains(config.PRAGMA_NO_OPTIMIZATION)) return true;
        for(Pair<SourceLine, SourceLine> block: optimizationProtectedBlocks) {
            if (sl.precedesEq(block.getRight()) &&
                block.getLeft().precedesEq(sl)) {
                return true;
            }
        }
        return false;
    }
    
}
