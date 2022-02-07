import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        @SuppressWarnings("resource")
        var scanner = new Scanner(System.in);
        var parts = scanner.nextLine().split("\\s+");

        var a = Short.parseShort(parts[0]);
        var b = Short.parseShort(parts[1]);

        if (a < b)
            System.out.print('<');
        else if (a > b)
            System.out.print('>');
        else
            System.out.print("==");
    }
}
