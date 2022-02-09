import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        @SuppressWarnings("resource")
        var scanner = new Scanner(System.in);
        var num1 = scanner.nextInt();
        var num2 = scanner.nextInt();

        var num2digit1 = num2 % 10;
        var num3 = num1 * num2digit1;

        var num2digit10 = (num2 % 100) / 10;
        var num4 = num1 * num2digit10;

        var num2digit100 = num2 / 100;
        var num5 = num1 * num2digit100;

        var num6 = num1 * num2;
        System.out.println(num3);
        System.out.println(num4);
        System.out.println(num5);
        System.out.println(num6);
    }
}
