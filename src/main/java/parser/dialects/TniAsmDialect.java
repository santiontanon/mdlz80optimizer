/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package parser.dialects;


import org.apache.commons.lang3.StringUtils;

import cl.MDLConfig;
import code.CodeBase;
import code.Expression;

/**
 * tniASM 0.45 Dialect
 * @author theNestruo
 */
public class TniAsmDialect implements Dialect {

    private final MDLConfig config;

    private String lastAbsoluteLabel;

    /**
     * Constructor
     * @param a_config
     */
    public TniAsmDialect(MDLConfig a_config) {
        super();

        config = a_config;

        lastAbsoluteLabel = null;

        config.preProcessor.macroSynonyms.put("ifexist", config.preProcessor.MACRO_IFDEF);

        config.lineParser.addKeywordSynonym("rb", config.lineParser.KEYWORD_DS);
        config.lineParser.addKeywordSynonym("rw", config.lineParser.KEYWORD_DS); // FIXME misses implicit x2
    }


    @Override
    public String newSymbolName(String name, Expression value) {

        // A relative label
        if (StringUtils.startsWith(name, ".")) {
            return lastAbsoluteLabel + name;
        }

        // When a name has "CURRENT_ADDRESS" as its value, it means it's a label.
        // If it does not start by ".", then it's an absolute label:
        if ((value != null)
                && (value.type == Expression.EXPRESSION_SYMBOL)
                && value.symbolName.equalsIgnoreCase(CodeBase.CURRENT_ADDRESS)) {
            lastAbsoluteLabel = name;
        }

        // An absolute label
        return name;
    }


    @Override
    public String symbolName(String name) {

        return StringUtils.startsWith(name, ".")
                ? lastAbsoluteLabel + name
                : name;
    }
}
