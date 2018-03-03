import java.io.Console;

public class ConsoleExample {

	public static void main(String[] args) {
		Console console;
		char[] password;
		while ((console = System.console()) != null &&
				(password = console.readPassword("Password: ")) != null) {
			console.printf("Confirm: %s", new String(password)).format("\n").flush();
			java.util.Arrays.fill(password, ' ');
		}
	}

}
