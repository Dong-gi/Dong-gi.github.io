import static org.junit.Assert.*;

import java.util.Arrays;

import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.collections4.iterators.ListIteratorWrapper;
import org.apache.commons.collections4.iterators.ObjectGraphIterator;
import org.junit.Test;

public class IteratorTest {

	@Test
	public void objectGraphIteratorTest() {
		var graph = new Object[] {
				new Object[] { 1, 2 },
				new Object[][] {{ 3, 4 }, { 5 }},
				null,
				Arrays.<Object>asList(6, 7),
				8
			};

		var iterator = new ObjectGraphIterator<Object>(graph, node -> {
			if(node == null) return null;
			var subIterator = new ListIteratorWrapper<Object>(IteratorUtils.getIterator(node));
			var isSingletonIterator = IteratorUtils.size(subIterator) == 1;
			subIterator.reset();
			return isSingletonIterator ? subIterator.next() : subIterator;
		});
		var iterText = IteratorUtils.toString(iterator);
		System.out.println(iterText);
		assertTrue(iterText.contentEquals(
				IteratorUtils.toString(Arrays.asList(1, 2, 3, 4, 5, null, 6, 7, 8).iterator()))); // OK
	}

}
