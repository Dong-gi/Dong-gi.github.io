import lombok.NonNull;

public class LombokNonNullExample {

    public static void main(String[] args) {
        print("Hello");
        print(null);
    }

    private static void print(@NonNull Object o) {
        System.out.println(o);
    }
    /*
Hello
Exception in thread "main" java.lang.NullPointerException: o is marked non-null but is null
     */
}