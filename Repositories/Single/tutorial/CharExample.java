class CharExample {
	public static void main(String[] args) {
		char c; // 2bytes 유니코드.
		c = '\u0000'; // == Character.MIN_CODE_POINT
		System.out.println("char은 내부적으로 정수로 다뤄진다. " + ('d' - 'a'));
		c = '\uFFFF'; // != Character.MAX_CODE_POINT
	}
}