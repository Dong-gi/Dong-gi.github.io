
public class Operator {

	public static void main(String[] args) {
		int num = -4;
		System.out.printf("num : %32s\n", Integer.toBinaryString(num));
		System.out.printf("<<  : %32s\n", Integer.toBinaryString(num << 10)); // 빈 비트를 0으로 채운다.
		System.out.printf(">>  : %32s\n", Integer.toBinaryString(num >> 10)); // 부호 비트를 유지한다.
		System.out.printf(">>> : %32s\n", Integer.toBinaryString(num >>> 10)); // 비어진 비트를 0으로 채운다.

		System.out.println(3/0. == Double.POSITIVE_INFINITY);
		System.out.println(Double.isInfinite(3/0.));
		System.out.println(0./0. == Double.NaN);
		System.out.println(Double.isNaN(0./0.));
		try {
			double val = 3/0;
		} catch (Exception e) { e.printStackTrace(); }
	}

}
