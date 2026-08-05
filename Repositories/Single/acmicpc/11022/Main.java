import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        @SuppressWarnings("resource")
        var scanner = new Scanner(System.in);
        var testCount = scanner.nextInt();

        for (var x = 1; x <= testCount; ++x) {
            var a = scanner.nextInt();
            var b = scanner.nextInt();
            System.out.printf("Case #%d: %d + %d = %d\n", x, a, b, a + b);
        }
    }
}
