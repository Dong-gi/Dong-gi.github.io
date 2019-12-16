import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;

import org.apache.commons.collections4.CollectionUtils;
import org.junit.Test;








public class CollectionUtilsTest { // 15 고정

	@Test
	public void unionTest() {
		System.out.println(Arrays.toString(
				CollectionUtils.union(
						new HashSet<Integer>() {{ add(1); add(1); add(2); }},
						new HashSet<Integer>() {{ add(1); add(3); }}
				).toArray())); // [1, 2, 3]

		System.out.println(Arrays.toString(
				CollectionUtils.union(
						new LinkedList<Integer>() {{ add(1); add(1); add(2); }},
						new LinkedList<Integer>() {{ add(1); add(3); }}
				).toArray())); // [1, 1, 2, 3]
	}

}
