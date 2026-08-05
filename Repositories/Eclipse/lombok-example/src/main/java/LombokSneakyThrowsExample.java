import lombok.SneakyThrows;

public class LombokSneakyThrowsExample {
    @SneakyThrows
    public static void noThrowsDeclaration() {
        Thread.sleep(100);
    }

    public static void main(String[] args) {
        noThrowsDeclaration();
        System.out.println("hello"); // hello
    }
}