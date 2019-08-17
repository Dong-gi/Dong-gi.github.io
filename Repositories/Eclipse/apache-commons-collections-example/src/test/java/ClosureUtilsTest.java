import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.collections4.Closure;
import org.apache.commons.collections4.ClosureUtils;
import org.apache.commons.collections4.Predicate;
import org.junit.Test;





public class ClosureUtilsTest { // 15 고정

	@Test
	public void whileClosureTest() throws InterruptedException {
		final var counter = new AtomicInteger();
		var thread = new Thread(() -> {
			var print = ClosureUtils.whileClosure(
							(Integer num) -> num < 10,
							(Integer num) -> {
								num = num + 1;
								counter.getAndAdd(1);
							});
			print.execute(5);
		});
		thread.start();
		thread.join(100);
		thread.interrupt();
		assertTrue(String.format("%d < 10?", counter.get()), counter.get() < 10);
		// java.lang.AssertionError: 13190961 < 10?
	}

	@Test
	public void invokerClosureTest() {
		var newLinePrinter = ClosureUtils.invokerClosure("println");
		var helloPrinter = ClosureUtils.invokerClosure("print", new Class<?>[] { String.class }, new Object[] { "Hello" });
		helloPrinter.execute(System.out);
		newLinePrinter.execute(System.out);
		helloPrinter.execute(System.out);
		/** 결과
		 * Hello
		 * Hello
		 */
	}
	
	@Test
	public void chainedClosureTest() {
		Closure<Integer> plus1 = i -> i += 1,
						 plus2 = i -> i += 2,
						 plus3 = i -> i += 3;
		var chain = ClosureUtils.chainedClosure(plus1, plus2, plus3);
		Integer i = 0;
		chain.execute(i);
		assertTrue(String.format("%d = 6?", i), i == 6);
		// java.lang.AssertionError: 0 = 6?
	}
	
	@Test
	public void switchClosureTest() {
		Predicate<Integer> alwaysTrue = x -> true;
		Predicate<Integer> alwaysFalse = x -> false;
		Closure<Integer> sayTrue = x -> System.out.println(true);
		Closure<Integer> sayFalse = x -> System.out.println(false);
		
		Predicate<Integer>[] predicates = new Predicate[] { alwaysFalse, alwaysTrue, alwaysFalse, alwaysTrue };
		Closure<Integer>[] closures = new Closure[] { sayFalse, sayTrue, sayFalse, sayTrue };
		ClosureUtils.switchClosure(predicates, closures, System.out::println).execute(0);
		
		ClosureUtils.switchMapClosure(new HashMap<Integer, Closure<Integer>>() {{
			put(0, sayFalse); put(1, sayTrue); put(null, x -> System.out.println("No entry"));
		}}).execute(10);
	}
}
