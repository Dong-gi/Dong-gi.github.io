import static org.junit.Assert.assertTrue;

import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.junit.Test;










public class MultiValuedMapTest { // 15 고정
	
	@Test
	public void containsValueTest() {
		var map = new ArrayListValuedHashMap<Integer, String>();
		map.put(1, "apple");
		map.put(1, "ananas");
		map.put(2, "banana");
		assertTrue(map.containsValue("ananas")); // OK
	}
	
	@Test
	public void mapIteratorTest() {
		var map = new ArrayListValuedHashMap<Integer, Integer>();
		map.put(1, 1);
		map.put(1, 1);
		map.put(1, 2);
		map.put(2, 3);
		map.removeMapping(2, 3);
		map.get(2).addAll(map.get(1));
		for(var iterator = map.mapIterator(); iterator.hasNext(); )
			System.out.println(String.format("key : %d, value : %d", iterator.next(), iterator.getValue()));
		/**
		 * key : 1, value : 1
		 * key : 1, value : 1
		 * key : 1, value : 2
		 * key : 2, value : 1
		 * key : 2, value : 1
		 * key : 2, value : 2
		 */
	}

}
