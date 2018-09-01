package io.github.donggi;

public class TypeNumber {

    public static void main(String[] args) {
        short s; // 2bytes, -2^15 ~ 2^15-1
        s = 012;
        System.out.println("0 : 8진법 리터럴 " + s);

        int i; // 4bytes, -2^31 ~ 2^31-1
        i = 0x12;
        System.out.println("0x : 16진법 리터럴 " + i);

        i = 0b01111111;
        System.out.println("0b : 2진법 리터럴 " + i);

        long l; // 8bytes, -2^63 ~ 2^63-1
        l = 123_456_789_000L;
        System.out.println("-L or -l : long 타입 리터럴 " + l);
        System.out.println(123 + 0x123 + 0123 + 0b101101);

        float f; // 4바이트, IEEE 754
        f = 1.23F; // float 리터럴
        System.out.println("float : " + f);

        double d; // 8바이트, IEEE 754
        d = 123_456_000 + 1.234_567 + (1.234E+2);
        System.out.println("double : " + d);

        var num = -4;
        System.out.printf("num : %32s\n", Integer.toBinaryString(num));
        System.out.printf("<<  : %32s\n", Integer.toBinaryString(num << 10)); // 빈 비트를 0으로 채운다.
        System.out.printf(">>  : %32s\n", Integer.toBinaryString(num >> 10)); // 부호 비트를 유지한다.
        System.out.printf(">>> : %32s\n", Integer.toBinaryString(num >>> 10)); // 비어진 비트를 0으로 채운다.

        System.out.println(3 / 0. == Double.POSITIVE_INFINITY);
        System.out.println(Double.isInfinite(3 / 0.));
        System.out.println(0. / 0. == Double.NaN);
        System.out.println(Double.isNaN(0. / 0.));
        try {
            var val = 3 / 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
