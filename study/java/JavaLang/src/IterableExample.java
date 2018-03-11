import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;

public class IterableExample {

	public static void main(String[] args) {
		ArrayList<Integer> nums = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5));
		
		Iterator<Integer> iter = nums.iterator();
		iter.forEachRemaining(new Consumer<Integer>() {
			@Override
			public void accept(Integer i) {
				System.out.println(">> " + i);
			}
		});
		
		Spliterator<Integer> split = nums.spliterator();
		split.forEachRemaining((i) -> System.out.println("<< " + i));
	}

}
