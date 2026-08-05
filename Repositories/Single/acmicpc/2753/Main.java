import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        @SuppressWarnings("resource")
        var scanner = new Scanner(System.in);
        var year = scanner.nextInt();

        if (year % 4 == 0 && (year % 400 == 0 || year % 100 != 0))
            System.out.println(1);
        else
            System.out.println(0);
    }
}
