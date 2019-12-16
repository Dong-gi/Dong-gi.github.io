package io.github.donggi;

import java.util.Arrays;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StreamExample {

    public static void main(String[] args) {
        System.out.println(
                new Random()
                    .ints(10)
                    .mapToObj(String::valueOf)
                    .collect(Collectors.joining(", "))
                );

        System.out.println(
                Arrays.toString(
                        Stream.iterate(0, i -> i+2).limit(20).toArray()
                    )
                );
    }

}
