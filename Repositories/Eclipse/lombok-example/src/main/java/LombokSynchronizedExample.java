import lombok.Synchronized;

public class LombokSynchronizedExample {
    private final Object myLock = new Object();

    @Synchronized
    public static void hello() {
      System.out.println("Hello");
    }

    @Synchronized
    public void world() {
        System.out.println("World");
    }

    @Synchronized("myLock")
    public void foo() {
      System.out.println("bar");
    }
}