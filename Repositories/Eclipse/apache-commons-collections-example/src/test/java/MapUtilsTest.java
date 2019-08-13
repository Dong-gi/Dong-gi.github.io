import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.collections4.MapUtils;
import org.junit.Before;
import org.junit.Test;

import lombok.Builder;
import lombok.Data;

public class MapUtilsTest {

	@Data
	@Builder
	static class MItem {
		Integer itemId;
		String itemName;
	}
	
	static Map<Integer, MItem> items = new HashMap<>();
	
	@Before
	public void before() {
		for(var id = 1; id < 3; ++id) {
			var mItem = new MItem(id, "item" + id);
			items.put(mItem.getItemId(), mItem);
		}
	}
	
	@Test
	public void getStringTest() {
		for(var id = 2; id < 4; ++id)
			System.out.println(MapUtils.getString(items, id));
		/**
		 * MapUtilsTest.MItem(itemId=2, itemName=item2)
		 * null
		 */
	}
	
	@Test
	public void printTest() {
		MapUtils.verbosePrint(System.out, "MItem", items);
		/**
		 * MItem =
		 * {
		 *     1 = MapUtilsTest.MItem(itemId=1, itemName=item1)
		 *     2 = MapUtilsTest.MItem(itemId=2, itemName=item2)
		 * }
		 */
		MapUtils.debugPrint(System.out, "MItem", items);
		/**
		 * MItem = 
		 * {
		 *     1 = MapUtilsTest.MItem(itemId=1, itemName=item1) MapUtilsTest$MItem
		 *     2 = MapUtilsTest.MItem(itemId=2, itemName=item2) MapUtilsTest$MItem
		 * } java.util.HashMap
		 */
	}
	
	@Test
	public void invertMapTest() {
		assertTrue(MapUtils.invertMap(items).get(items.get(1)) == 1); // OK
	}

	@Test
	public void populateMapTest() {
		var out1 = new ByteArrayOutputStream();
		var out2 = new ByteArrayOutputStream();
		
		var items2 = new HashMap<Integer, MItem>();
		MapUtils.populateMap(
				items2,
				Arrays.asList(1, 2),
				id -> id,
				id -> new MItem(id, "item" + id));
		
		MapUtils.verbosePrint(new PrintStream(new ByteArrayOutputStream()), null, items);
		MapUtils.verbosePrint(new PrintStream(new ByteArrayOutputStream()), null, items2);
		
		assertTrue(out1.toString().contentEquals(out2.toString())); // OK
	}
}
