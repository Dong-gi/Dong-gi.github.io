import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        @SuppressWarnings("resource")
        var scanner = new Scanner(System.in);
        var testCount = scanner.nextInt();

        for (var x = 1; x <= testCount; ++x)
            System.out.printf("Case #%d: %d\n", x, scanner.nextInt() + scanner.nextInt());
    }
}
