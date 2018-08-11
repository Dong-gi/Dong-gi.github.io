package io.github.donggi;

public class TypeBoolean {

	public static void main(String[] args) {
		boolean b1;
		b1 = true; // true literal
		b1 = false; // false literal
		
		System.out.println(sayFalse() & sayTrue());
		System.out.println(sayFalse() && sayTrue());

		System.out.println(sayTrue() | sayFalse());
		System.out.println(sayTrue() || sayFalse());
	}

	public static boolean sayFalse() {
		System.out.println("거짓말!");
		return false;
	}

	public static boolean sayTrue() {
		System.out.println("속았지?");
		return true;
	}

}
