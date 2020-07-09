/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package workers;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cl.MDLConfig;
import code.CodeBase;
import code.Expression;
import code.SourceConstant;
import parser.Tokenizer;

/**
 *
 * @author santi
 */
public class SymbolTableGenerator implements MDLWorker {

    MDLConfig config = null;

    String outputFileName = null;
    boolean includeConstants = false;

    public SymbolTableGenerator(MDLConfig a_config)
    {
        config = a_config;
    }

    @Override
    public String docString() {
        return "  -st <output file>: to output the symbol table.\n" +
               "  -st-constants: includes constants, in addition to labels, in the output symbol table.\n";
    }

    @Override
    public boolean parseFlag(List<String> flags) {
        if (flags.get(0).equals("-st") && flags.size()>=2) {
            flags.remove(0);
            outputFileName = flags.remove(0);
            return true;
        }
        if (flags.get(0).equals("-st-constants")) {
            flags.remove(0);
            includeConstants = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean work(CodeBase code) {

        if (outputFileName != null) {
            config.debug("Executing "+this.getClass().getSimpleName()+" worker...");

            try (FileWriter fw = new FileWriter(outputFileName)) {
                fw.write(symbolTableString(code));
                fw.flush();
            } catch (Exception e) {
                config.error("Cannot write to file " + outputFileName + ": " + e);
                return false;
            }
        }

        return true;
    }


    public String symbolTableString(CodeBase code)
    {
        List<String> sortedSymbols = new ArrayList<>();
        sortedSymbols.addAll(code.getSymbols());
        Collections.sort(sortedSymbols);
        StringBuilder sb = new StringBuilder();
        for(String name:sortedSymbols) {
            SourceConstant symbol = code.getSymbol(name);
            if (symbol.exp.type == Expression.EXPRESSION_SYMBOL &&
                symbol.exp.symbolName.equalsIgnoreCase(CodeBase.CURRENT_ADDRESS) ||
                includeConstants) {
                sb.append(name);
                sb.append(": equ ");
                if (symbol.exp.isConstant()) {
                    sb.append(symbol.getValue(code, true));
                } else if (symbol.isLabel()) {
                    sb.append(Tokenizer.toHexWord(symbol.getValue(code, true), config.hexStyle));
                } else {
                    sb.append(symbol.getValue(code, true));
                    sb.append("  ; ");
                    sb.append(symbol.exp);
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }
}
