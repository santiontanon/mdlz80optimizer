/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package code;

import cl.MDLConfig;
import java.util.ArrayList;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import parser.SourceLine;
import util.Pair;
import workers.pattopt.ExecutionFlowAnalysis;
import workers.pattopt.ExecutionFlowAnalysis.StatementTransition;

public class CodeBase {    
    public static final String CURRENT_ADDRESS = "$";

    MDLConfig config;

    LinkedHashMap<String, SourceFile> sources = new LinkedHashMap<>();
    LinkedHashMap<String, SourceConstant> symbols = new LinkedHashMap<>();
    
    // Stores the mdl:no-opt-start / mdl:no-opt-end protected blocks of code:
    public List<Pair<SourceLine, SourceLine>> optimizationProtectedBlocks = new ArrayList<>();

    // List of the expected output binaries:
    public List<OutputBinary> outputs = new ArrayList<>();

    // Table with all the transitions that a given statement can have (e.g. jumps, rets, etc.).
    public HashMap<CodeStatement, List<StatementTransition>> executionFlowTable = null;


    public CodeBase(MDLConfig a_config)
    {
        config = a_config;
    }


    public static boolean isRegister(String name)
    {
        String registers[] = {"a", "b", "c", "d", "e", "h", "l",
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

    
    public static boolean is8bitRegister(String name)
    {
        String registers[] = {"a", "b", "c", "d", "e", "h", "l",
                              "ixl", "ixh", "iyl", "iyh",
                              "af'",
                              "i", "r"};
        for(String reg:registers) {
            if (name.equalsIgnoreCase(reg)) return true;
        }

        return false;
    }


    public static boolean isBase8bitRegister(String name)
    {
        String registers[] = {"a", "b", "c", "d", "e", "h", "l"};
        for(String reg:registers) {
            if (name.equalsIgnoreCase(reg)) return true;
        }

        return false;
    }
    
    
    public static boolean isRegisterPair(String name)
    {
        String registers[] = {"af", "bc", "de", "hl",
                              "sp", "ix", "iy", "pc","af'"};
        for(String reg:registers) {
            if (name.equalsIgnoreCase(reg)) return true;
        }

        return false;
    }
    
    
    public static String[] get8bitRegistersOfRegisterPair(String regpair)
    {
        switch(regpair.toUpperCase()) {
            case "AF": return new String[]{"A", "F"};
            case "AF'": return new String[]{"A'", "F'"};
            case "BC": return new String[]{"B", "C"};
            case "DE": return new String[]{"D", "E"};
            case "HL": return new String[]{"H", "L"};
            case "IX": return new String[]{"IXH", "IXL"};
            case "IY": return new String[]{"IYH", "IYL"};
        }
        return null;
    }
    

    public static boolean isCondition(String name)
    {
        String conditions[] = {"c", "m", "nc", "nz", "p", "pe", "po", "z"};
        for(String c:conditions) {
            if (name.equalsIgnoreCase(c)) return true;
        }

        return false;
    }

    
    public SourceConstant sizeOfLabelConstant(String underlyingLabel, String name) {
        SourceConstant underlyingSC = getSymbol(underlyingLabel);
        if (underlyingSC == null) {
            return null;
        }
        // Find the next label:
        CodeStatement s = underlyingSC.definingStatement;
        CodeStatement s2 = s.source.getNextStatementTo(s, this);
        while(s2 != null) {
            if (s2.label != null && s2.label.relativeTo == null) {
                break;
            }
            s2 = s2.source.getNextStatementTo(s2, this);
        }
        if (s2 == null) return null;
        Expression sizeOfExpression = Expression.operatorExpression(Expression.EXPRESSION_SUB, 
                Expression.symbolExpression(s2.label.name, s2, this, config),
                Expression.symbolExpression(underlyingLabel, s, this, config), config);
        SourceConstant sc = new SourceConstant(name, name, sizeOfExpression, s, config);
        addSymbol(name, sc);
        return sc;
    }
    

    public SourceConstant getSymbol(String name)
    {
        if (symbols.containsKey(name)) {
            return symbols.get(name);
        } else if (config.allowWLADXSizeOfSymbols && name.startsWith("_sizeof_")) {
            return sizeOfLabelConstant(name.substring("_sizeof_".length()), name);
        }
        return null;
    }

    
    // Find and return any symbol that starts with 'prefix'
    public SourceConstant getSymbolWithPrefix(String prefix)
    {
        if (symbols.containsKey(prefix)) {
            return symbols.get(prefix);
        }
        for(String symbol:symbols.keySet()) {
            if (symbol.startsWith(prefix)) {
                return symbols.get(symbol);
            }
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
        } else if (config.allowWLADXSizeOfSymbols && name.startsWith("_sizeof_")) {
            return sizeOfLabelConstant(name.substring("_sizeof_".length()), name);
        }
        return null;
    }
    

    public Object getSymbolValueInternal(String name, boolean silent, List<String> variableStack)
    {
        if (symbols.containsKey(name)) {
            return symbols.get(name).getValueInternal(this, silent, null, variableStack);
        } else if (config.allowWLADXSizeOfSymbols && name.startsWith("_sizeof_")) {
            return sizeOfLabelConstant(name.substring("_sizeof_".length()), name);
        }
        return null;
    }

    
    public void removeSymbol(String name)
    {
        symbols.remove(name);
    }
    
    
    public boolean wouldAddingSymbolResultInAnError(String name, SourceConstant sc)
    {
        if (symbols.containsKey(name)) {
            SourceConstant previous = symbols.get(name);
            return !previous.resolveEagerly;
        } else {
            return false;
        }
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
        if (isRegister(name)) {
            config.warn("Defining '" + name + "' as a symbol, which collides with the name of a register.");
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


    public void resetAddressesAndFlow()
    {
        for(SourceFile f:sources.values()) {
            f.resetAddresses();
        }
        for(SourceConstant c:symbols.values()) {
            c.valueCache = null;
        }
        
        executionFlowTable = null;
        if (config.flowAnalyzer != null) config.flowAnalyzer.reset();
    }

    
    public OutputBinary addOutput(String binaryName, SourceFile main, int minimumSize)
    {
        OutputBinary output = new OutputBinary(binaryName, main, minimumSize);
        outputs.add(output);
        return output;
    }


    public void evaluateAllExpressions()
    {
        for(SourceFile f:sources.values()) {
            f.evaluateAllExpressions(this);
        }
    }
    
    
    public boolean isSelfModifying(CodeStatement s) 
    {
        return s.comment != null && s.comment.contains(config.PRAGMA_SELF_MODIFYING);
    }
    
    
    public boolean protectedFromOptimization(CodeStatement s) 
    {
        if (s.comment != null && s.comment.contains(config.PRAGMA_NO_OPTIMIZATION)) return true;
        if (s.comment != null && s.comment.contains(config.PRAGMA_SELF_MODIFYING)) return true;
        return protectedFromOptimization(s.sl);
    }


    public boolean protectedFromOptimization(SourceLine sl) 
    {
        if (sl == null || sl.line == null) return false;
        if (sl.line.contains(config.PRAGMA_NO_OPTIMIZATION)) return true;
        if (sl.line.contains(config.PRAGMA_SELF_MODIFYING)) return true;
        for(Pair<SourceLine, SourceLine> block: optimizationProtectedBlocks) {
            if (sl.precedesEq(block.getRight()) &&
                block.getLeft().precedesEq(sl)) {
                return true;
            }
        }
        return false;
    }


    public CodeStatement checkRelativeJumpsInRange()
    {
        for(SourceFile f:getSourceFiles()) {
            for(CodeStatement s:f.getStatements()) {
                if (s.type == CodeStatement.STATEMENT_CPUOP) {
                    if (s.op.isJump()) {
                        if (!s.op.labelInRange(s, this)) {
                            return s;
                        }
                    }
                }
            }
        }
        
        return null;
    }    
    
    
    // If a local label has gotten out of the scope of its associated absolute label,
    // it must be turned into an absolute label:
    // Returns true if any label was fixed (only fixes one at a time):
    public boolean fixLocalLabels(CodeStatement s)
    {
        List<SourceConstant> localLabels = new ArrayList<>();
        if (s.label != null && s.label.relativeTo != null) {
            // relative label!
            localLabels.add(s.label);
        } else {
            List<Expression> exps = s.getAllExpressions();
            for(Expression exp:exps) {
                for(String symbol:exp.getAllSymbols()) {
                    SourceConstant sc = this.getSymbol(symbol);
                    if (sc != null && sc.relativeTo != null) {
                        // jumping to a relative label!
                        localLabels.add(sc);
                    }
                }
            }
        }
        for(SourceConstant label:localLabels) {
            if (label != null) {
                boolean found = false;
                String previousLabel = null;
                CodeStatement s2 = s;
                while(s2 != null) {
                    if (s2.label != null && s2.label.relativeTo == null) {
                        // absolute label:
                        if (label.relativeTo == s2.label) {
                            found = true;
                        }
                        previousLabel = s2.label.name;
                        break;
                    }
                    s2 = s2.source.getPreviousStatementTo(s2, this);
                }
                if (!found) {
                    // we found a local label out of context!
                    config.debug("CodeReorganizer: local label out of context! " + label.originalName + " should in the context of " + label.relativeTo.originalName + " but isn't (previous absolute label was: "+previousLabel+")!");

                    // turn the local label into an absolute label:
                    label.relativeTo = null;
                    label.originalName = label.name;

                    return true;
                }
            }
        }
        return false;
    }
    
    
    public List<StatementTransition> getStatementPossibleDestinations(CodeStatement s)
    {
        if (!config.useExecutionFlowAnalysis) return null;

        if (config.flowAnalyzer == null) {
            config.flowAnalyzer = new ExecutionFlowAnalysis(this, config);
        }
        if (executionFlowTable == null) {
            executionFlowTable = config.flowAnalyzer.findAllRetDestinations();
        }
        if (executionFlowTable != null) {
            return executionFlowTable.get(s);
        }
        return null;
    }
}
