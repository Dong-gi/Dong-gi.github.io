import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class Main {

    public static void main(String[] args) {
        @SuppressWarnings("resource")
        var scanner = new Scanner(System.in);
        scanner.hasNext();

        while (true) {
            var cf = CompletableFuture.supplyAsync(() -> scanner.nextInt() + scanner.nextInt());

            try {
                System.out.println(cf.get(100, TimeUnit.MILLISECONDS));
            } catch (Exception e) {
                System.exit(0);
            }
        }
    }

}
