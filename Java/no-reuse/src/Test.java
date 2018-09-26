import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Test {
    public static void main(String[] args) {
        List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8);
        List<Integer> twoEvenSquares =
                numbers.parallelStream()
                .filter(n -> {
                    System.out.println("filtering " + n);
                    System.out.println("Current Thread : " + Thread.currentThread().getId());
                    return n % 2 == 0;
                })
                .map(n -> {
                    System.out.println("mapping " + n);
                    System.out.println("Current Thread : " + Thread.currentThread().getId());
                    return n * n;
                })
                .limit(2)
                .collect(Collectors.toList());

        // Stream.iterate(seed, unaryOperator);
        Stream.iterate(new int[] {0, 1}, new UnaryOperator<int[]>() {
            @Override
            public int[] apply(int[] t) {
                return new int[]{t[1],t[0] + t[1]};
            }
        });
        Stream.iterate(new int[]{0, 1}, (int[] t) -> new int[]{t[1],t[0] + t[1]}).limit(10);
    }
}
