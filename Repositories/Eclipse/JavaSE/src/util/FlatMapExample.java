package util;

import java.util.stream.IntStream;

public class FlatMapExample {

    public static void main(String[] args) {
        makeNLengthIntegers(2).forEach(System.out::println);
    }
    
    static IntStream makeNLengthIntegers(int n) {
        IntStream initial = IntStream.iterate(1, x -> x+1).limit(9);
        for(var i = 1; i < n; ++i)
            initial = initial.flatMap(FlatMapExample::make);
        return initial;
    }
    
    static IntStream make(int x) {
        final var base = 10 * x;
        return IntStream.iterate(0, n -> n + 1)
                        .map(n -> n+base)
                        .limit(10);
    }

}
