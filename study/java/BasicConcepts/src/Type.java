
public class Type {

	public static boolean sayFalse() {
		System.out.println("Say: false");
		return false;
	}

	public static boolean sayTrue() {
		var result = true;
		System.out.println("Say: " + result);
		return result;
	}

	public static void main(String[] args) {
		boolean var01;
		var01 = true; // true literal
		var01 = false; // false literal

		System.out.println("& : logical and operator");
		System.out.println(sayFalse() & sayTrue());

		System.out.println("&& : logical and operator(short circuit)");
		System.out.println(sayFalse() && sayTrue());

		System.out.println("| : logical or operator");
		System.out.println(sayTrue() | sayFalse());

		System.out.println("|| : logical or operator(short circuit)");
		System.out.println(sayTrue() || sayFalse());

		System.out.println("'a |= b' is equals to 'a = a | b'. There's no ||= operator");
		var01 |= sayTrue();

		System.out.println("'a &= b' is equals to 'a = a & b'. There's no &&= operator");
		var01 &= sayTrue();

		char var02; // 2bytes UNICODE.
		var02 = '\u0000'; // == Character.MIN_CODE_POINT
		System.out.println("char is regarded as a integer internally. " + ('d' - 'a'));
		var02 = '\uFFFF'; // != Character.MAX_CODE_POINT

		byte var03; // 1byte, -128 ~ 127
		var03 = 0b00001000;
		System.out.println("0b means binary integer. " + var03);

		short var04; // 2bytes, -2^15 ~ 2^15-1
		var04 = 012;
		System.out.println("0- means octal integer. " + var04);

		int var05; // 4bytes, -2^31 ~ 2^31-1
		var05 = 0x12;
		System.out.println("0x means hexadecimal integer. " + var05);

		long var06; // 8bytes, -2^63 ~ 2^63-1
		var06 = 123L;
		System.out.println("-L or -l means Long type integer. " + var06);
		System.out.println(123 + 0x123 + 0123 + 0b101101);

		float var07; // 4바이트, IEEE 754
		var07 = 1.23F; // float 리터럴
		System.out.println("float : " + var07);

		double var08; // 8바이트, IEEE 754
		var08 = 123_456_000 + 1.234_567 + (1.234E+2);
		System.out.printf("double : %f", var08);
	}

}
