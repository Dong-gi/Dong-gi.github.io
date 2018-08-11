package io.github.donggi;

public class TypeCharacter {

	public static void main(String[] args) {
		char c; // 2bytes 유니코드.
		c = '\u0000'; // == Character.MIN_CODE_POINT
		System.out.println("char은 내부적으로 int로 다뤄진다. " + ('d' - 'a'));
		c = '\uFFFF'; // != Character.MAX_CODE_POINT
	}

}
