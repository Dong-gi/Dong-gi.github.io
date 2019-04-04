package io.github.donggi;

public class TypeExample {

    public static void booleanExample() {
        boolean flag;
        flag = true; // true literal
        flag = false; // false literal

        System.out.println(sayFalse() & sayTrue());
        System.out.println(sayFalse() && sayTrue()); // Short-circuit

        System.out.println(sayTrue() | sayFalse());
        System.out.println(sayTrue() || sayFalse()); // Short-circuit
    }

    public static boolean sayFalse() {
        System.out.print("거짓말입니다. ");
        return false;
    }

    public static boolean sayTrue() {
        System.out.print("참말입니다. ");
        return true;
    }
    
    public static void stringExample() {
        String s = "Hello World";

        s = s + 123 + false;
        s += 1.23;
        s += 'H';
        s += "ello World";

        System.out.println(s);
    }
    
    public static void charExample() {
        char c; // 2bytes 유니코드.
        c = '\u0000'; // == Character.MIN_CODE_POINT
        System.out.println("char은 내부적으로 정수로 다뤄진다. " + ('d' - 'a'));
        c = '\uFFFF'; // != Character.MAX_CODE_POINT
    }
    
    public static void numberExample() {
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

        // 0.0으로 나눈 결과를 비교할 때는 예시와 같이 Double 클래스 메서드를 이용하는 것이 정확하다.
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
    
    public static void arrayExample() {
        int[] arr1;
        int arr2[];
        arr1 = new int[10];
        arr2 = new int[] { 1, 2, 3 };

        // 자바의 다차원 배열은 기본적으로 Jagged Array
        int[][] arr3 = new int[2][];
        arr3[0] = new int[10];
        arr3[1] = new int[20];

        var arr4 = new int[][] { { 1, 2, 3 }, { 4, 5, 6, 7 } };
    }

    public static void main(String[] args) {
        //booleanExample();
        //stringExample();
        //charExample();
        numberExample();
    }
}
