import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        @SuppressWarnings("resource")
        var scanner = new Scanner(System.in);
        var numCount = scanner.nextInt();

        var min = Integer.MAX_VALUE;
        var max = Integer.MIN_VALUE;

        for (var x = 1; x <= numCount; ++x) {
            var num = scanner.nextInt();
            if (num < min)
                min = num;
            if (max < num)
                max = num;
        }
        System.out.printf("%d %d", min, max);
    }
}
