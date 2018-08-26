package io.github.donggi;

import java.util.Random;

public class StreamExample {

	public static void main(String[] args) {
		new Random().ints(10).forEach(i -> System.out.println(i));
	}

}
