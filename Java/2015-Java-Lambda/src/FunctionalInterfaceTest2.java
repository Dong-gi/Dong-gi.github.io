import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;

@FunctionalInterface
interface Max<T extends Comparable>{
	T compute(T o1, T o2);
}


public class FunctionalInterfaceTest2 {

	public static void main(String[] args) {
		Function<Integer, Integer> square = x -> x*x;
		BiFunction<String, String, String> concat = (x, y) -> x + ", " +y;
		BiPredicate<String,Integer> isLessThan = (x, y) -> x.length()<y;
		Max<String> max = (x, y)->x.compareTo(y)>=0? x: y;
		
		System.out.println(square.apply(4));
		System.out.println(concat.apply("CSE", "EEC"));
		System.out.println(isLessThan.test("KOREA", 4));
		System.out.println(max.compute("CSE", "EEC"));
	}

}
