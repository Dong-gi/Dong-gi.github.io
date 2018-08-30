package lang;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;

public class IterableExample {

	public static void main(String[] args) {
		var nums = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5));
		
		var iter = nums.iterator();
		iter.forEachRemaining(new Consumer<Integer>() {
			@Override
			public void accept(Integer i) {
				System.out.println(">> " + i);
			}
		});
		
		var split = nums.spliterator();
		split.forEachRemaining((i) -> System.out.println(i + " <<"));
	}

}
