import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        @SuppressWarnings("resource")
        var scanner = new Scanner(System.in);
        var x = scanner.nextInt();
        var y = scanner.nextInt();
        if (x > 0)
            System.out.print(y > 0 ? 1 : 4);
        else
            System.out.print(y > 0 ? 2 : 3);
    }

}
