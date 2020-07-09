/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package parser.dialects;

import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import cl.MDLConfig;
import code.CodeBase;
import code.Expression;
import code.SourceFile;
import code.SourceStatement;

/**
 * tniASM 0.45 Dialect
 * @author theNestruo
 */
public class TniAsmDialect implements Dialect {
    /*
     * FIXME The tniASM dialect is not functional yet!
     */

    /** tniASM keywords (lowercase) */
    private final String[] keywords = { "org", "db", "dw", "dd", "ds" };

    /** The minimum number of tokens for each {@link #keywords tniASM keyword} */
    private final int[] minTokens = { 2, 2, 2, 2, 2 };

    private final MDLConfig config;

    private String lastAbsoluteLabel;

    /**
     * Constructor
     */
    public TniAsmDialect(MDLConfig a_config) {
        super();

        config = a_config;

        lastAbsoluteLabel = null;
    }


    @Override
    public boolean recognizeIdiom(List<String> tokens) {
        return getKeyword(tokens) != null;
    }


    /**
     * @param tokens the tokens
     * @return one of the {@link #keywords}, if the first token matches the keyword and there are enough tokens;
     * {@code null} otherwise
     */
    private String getKeyword(List<String> tokens) {

        // (sanity check)
        if ((tokens == null) || tokens.isEmpty()) {
            return null;
        }

        int index = ArrayUtils.indexOf(keywords, StringUtils.lowerCase(tokens.iterator().next()));
        return (index >= 0) && (tokens.size() >= minTokens[index])
                ? keywords[index]
                : null;
    }


    @Override
    public String newSymbolName(String name, Expression value) {

        // A keyword
        if (StringUtils.equalsAnyIgnoreCase(name, keywords)) {
            return null;
        }

        // A relative label
        if (StringUtils.startsWith(name, ".")) {
            return lastAbsoluteLabel + name;
        }

        // $ (??)
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


    @Override
    public List<SourceStatement> parseLine(List<String> tokens,
            String line, int lineNumber,
            SourceStatement s, SourceFile source, CodeBase code)
    {
        String keyword = getKeyword(tokens);
        if (keyword == null) {
            return null;
        }

        tokens.remove(0);
        return Collections.singletonList(s);
    }
}
