package parser.dialects;

import org.apache.commons.lang3.StringUtils;

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
		return new String[] { "mdl", "asmsx", "glass", "sjasm", "tniasm", "winape", "pasmo"};
	}

	/**
	 * @param dialect a dialect candidate
	 * @return a known dialect, or {@code null} if the candidate is not a known dialect
	 */
	public static String asKnownDialect(String dialect) {

		return StringUtils.equalsAnyIgnoreCase(dialect, knownDialects())
				? StringUtils.lowerCase(dialect)
				: null;
	}

	/**
	 * @param pDialect a dialect candidate
	 * @param mdlConfig MDLConfig instance
	 * @return a Dialect instance for the requested dialect,
	 * or {@code null} if the candidate is not a known dialect or has no Dialect implementation
	 */
	public static Dialect getDialectParser(String pDialect, MDLConfig mdlConfig) {

		String dialect = asKnownDialect(pDialect);
		if (dialect == null) {
			return null;
		}

		switch (dialect) {
			case "asmsx": return new ASMSXDialect(mdlConfig);
			case "glass": return new GlassDialect(mdlConfig);
			case "sjasm": return new SjasmDialect(mdlConfig);
			case "tniasm": return new TniAsmDialect(mdlConfig);
			case "winape": return new WinAPEDialect(mdlConfig);
			case "pasmo": return new PasmoDialect(mdlConfig);
			default: return null;
		}
	}
}