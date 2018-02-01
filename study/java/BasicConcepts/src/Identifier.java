
public class Identifier {

	private class MyPrivateClass {}
	
	private static final int MY_PRIVATE_NUMBER = 7;
	
	private void myPrivateMethod() {}
	
	public static void main(String[] args) {
		int myNumber = 7;
		
		System.out.println();
		// System : It seems like a class. Because it starts with upper case.
		// . : Dot('.') means internal space
		// out : It seems like a variable. Because it starts with small case.
		// println() : It seems like a method. Because it starts with small case and followed by parentheses.
		
		// If you use a Eclipse or other IDE, you would know the out's type is 'public static final PrintStream'.
		// Yeah, it is a singleton and a constant. But for ease of typing, it consists of small case letters.
	}

}
