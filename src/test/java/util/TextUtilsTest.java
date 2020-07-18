package util;

import org.junit.Assert;
import org.junit.Test;


public class TextUtilsTest {

	@Test
	public void anyMatchesIgnoreCase_singleWildcard() {
		final String[] testLines = new String[]{
			"INFO: Pattern-based optimization in data/tests/test1.asm#6: Replace cp 0 with or a (1 bytes, 3 t-states saved)",
			"INFO: Pattern-based optimization in data/tests/test1.asm#15: Remove unused ld a,0 (2 bytes, 8 t-states saved)",
			"INFO: PatternBasedOptimizer: 2 patterns applied, 3 bytes, 11 t-states saved."
		};

		Assert.assertTrue(TextUtils.anyMatchesIgnoreCase(
			"INFO: Pattern-based optimization in *test1.asm#6: Replace cp 0 with or a (1 bytes, 3 t-states saved)",
			testLines));

		Assert.assertTrue(TextUtils.anyMatchesIgnoreCase(
			"INFO: Pattern-based optimization in *test1.asm#15: Remove unused ld a,0 (2 bytes, 8 t-states saved)",
			testLines));
	}

	@Test
	public void anyMatchesIgnoreCase_multipleWildcards() {
		final String[] testLines = new String[]{
			"INFO: Pattern-based optimization in data/tests/\\test29-include.asm#4 (expanded from data/tests/test29.asm#6): Replace ld a,0 with xor a (1 bytes, 3 t-states saved)",
			"INFO: Pattern-based optimization in data/tests/\\test29-include.asm#4 (expanded from data/tests/test29.asm#8): Replace ld a,0 with xor a (1 bytes, 3 t-states saved)",
			"INFO: PatternBasedOptimizer: 2 patterns applied, 2 bytes, 6 t-states saved."
		};

		Assert.assertTrue(TextUtils.anyMatchesIgnoreCase(
			"INFO: Pattern-based optimization in *test29-include.asm#4 (expanded from *test29.asm#6): Replace ld a,0 with xor a (1 bytes, 3 t-states saved)",
			testLines));

		Assert.assertTrue(TextUtils.anyMatchesIgnoreCase(
			"INFO: Pattern-based optimization in *test29-include.asm#4 (expanded from *test29.asm#8): Replace ld a,0 with xor a (1 bytes, 3 t-states saved)",
			testLines));
	}
}