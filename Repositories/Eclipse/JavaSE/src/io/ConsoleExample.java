package io;

import java.util.Arrays;
import java.util.Optional;

public class ConsoleExample {

	public static void main(String[] args) {
		var console = Optional.ofNullable(System.console());
		console.ifPresentOrElse(c -> System.out.println(c), () -> System.out.println("Run on cmd"));

		char[] password;
		while (console.isPresent() && (password = console.get().readPassword("Password: ")) != null) {
			console.get().printf("Confirm: %s", new String(password)).format("\n").flush();
			Arrays.fill(password, (char) 0);
		}
	}

}