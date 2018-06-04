import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JUnitExample {

	@Test
	public void assertTrueTest() {
		assertTrue(true);
	}

	@Test
	public void assertFalseTest() {
		assertFalse(false);
	}

}
