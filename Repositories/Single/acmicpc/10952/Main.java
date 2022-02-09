import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        @SuppressWarnings("resource")
        var scanner = new Scanner(System.in);
        while (true) {
            var a = scanner.nextInt();
            var b = scanner.nextInt();
            if ((a | b) == 0)
                break;
            System.out.println(a + b);
        }
    }
}
