import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public class Test {
    
    public static void main(String[] args) {
        Executors.newScheduledThreadPool(5).schedule(() -> {
            System.out.println("Hello");
        }, 0, TimeUnit.SECONDS);
        System.out.print("hello");
    }
}
