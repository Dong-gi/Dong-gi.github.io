import static org.junit.Assert.*;

import org.apache.commons.collections4.comparators.FixedOrderComparator;
import org.junit.Test;

public class ComparatorTest {

	@Test
	public void fixedOrderComparaterTest1() {
		var comparator = new FixedOrderComparator<Character>('h', 'd', 'a', 'f', 'b', 'e');
		comparator.add('c');
		assertTrue(comparator.compare('h', 'a') < 0);
		assertTrue(comparator.compare('h', 'c') < 0);
	}

	@Test
	public void fixedOrderComparaterTest2() {
		var comparator = new FixedOrderComparator<Character>('h', 'd', 'a', 'f', 'b', 'e');
		assertTrue(comparator.compare('h', 'a') < 0);
		comparator.add('c'); // Cannot modify a FixedOrderComparator after a comparison
		assertTrue(comparator.compare('h', 'c') < 0);
	}

}
