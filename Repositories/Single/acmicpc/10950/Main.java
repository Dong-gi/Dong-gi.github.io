import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        @SuppressWarnings("resource")
        var scanner = new Scanner(System.in);
        var testCount = scanner.nextInt();

        for (var x = 0; x < testCount; ++x)
            System.out.println(scanner.nextInt() + scanner.nextInt());
    }
}
