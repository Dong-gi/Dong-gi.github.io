package util;

import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

public class CollectionExample {

	@Test
	void retainAllTest() {
		var list3 = make(3);
		var list5 = make(5);

		assertTrue(list5.size() == 5);			// OK
		assertTrue(list5.containsAll(list3));	// OK
		assertTrue(list5.retainAll(list3));		// OK
		assertTrue(list5.size() == 3);			// OK
	}

	static List<Integer> make(int x) {
        return IntStream.iterate(0, n -> n + 1).limit(x).boxed().collect(Collectors.toList());
    }
}
