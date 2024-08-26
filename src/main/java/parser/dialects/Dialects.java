/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package parser.dialects;

import cl.MDLConfig;

/**
 * Utility class to handle dialect identifier strings
 */
public class Dialects {
    
    /**
     * @return the default dialect identifier string
     */
    public static String defaultDialect() {
        return knownDialects()[0];
    }

    /**
     * @return the known dialect identifier strings
     */
    public static String[] knownDialects() {
        return new String[]{"mdl", "asmsx", "asmsx-zilog", "glass", "sjasm",
                            "sjasmplus", "tniasm", "tniasm045", "tniasm10", "winape", "pasmo",
                            "sdcc", "sdasz80", "macro80", "wladx", "wladxz80"};
    }
    
    /**
     * @param dialect a dialect candidate
     * @return a known dialect, or {@code null} if the candidate is not a known
     * dialect
     */
    public static String asKnownDialect(String dialect) {

        for(String dialect2:knownDialects()) {
            if (dialect.equalsIgnoreCase(dialect2)) {
                return dialect.toLowerCase();
            }
        }
        return null;
    }

    /**
     * @param pDialect a dialect candidate
     * @param config MDLConfig instance
     * @return a Dialect instance for the requested dialect, or {@code null} if
     * the candidate is not a known dialect or has no Dialect implementation
     */
    public static Dialect getDialectParser(String pDialect, MDLConfig config) {

        String dialect = asKnownDialect(pDialect);
        if (dialect == null) {
            return null;
        }

        switch (dialect) {
            case "asmsx":
                return new ASMSXDialect(config, false);
            case "asmsx-zilog":
                return new ASMSXDialect(config, true);
            case "glass":
                return new GlassDialect(config);
            case "sjasm":
                return new SjasmDialect(config);
            case "sjasmplus":
                return new SjasmPlusDialect(config);
            case "tniasm":
                return new TniAsmDialect(config, TniAsmDialect.TNIASM045);
            case "tniasm045":
                return new TniAsmDialect(config, TniAsmDialect.TNIASM045);
            case "tniasm10":
                return new TniAsmDialect(config, TniAsmDialect.TNIASM10);
            case "winape":
                return new WinAPEDialect(config);
            case "pasmo":
                return new PasmoDialect(config);
            case "sdcc":
            case "sdasz80":
                return new SDCCDialect(config);
            case "macro80":
                return new Macro80Dialect(config);
            case "wladx":
            case "wladxz80":
                return new WLADXZ80Dialect(config);
            default:
                return null;
        }
    }
    
    
    public static boolean selectDialect(String dialectString, MDLConfig config) {
        config.dialect = Dialects.asKnownDialect(dialectString);
        return config.dialect != null;
    }
}
