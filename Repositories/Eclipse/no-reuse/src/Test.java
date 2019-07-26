import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public class Test {

    @SuppressWarnings("serial")
    public static void main(String[] args) {
        test(new ArrayList<Integer>() {{add(1);}});
        test(new ArrayList<Byte>() {{add((byte)1);}});
        test(new ArrayList<String>() {{add("Hello");}});
    }

    public static <T> void test(List<T> l) {
        System.out.println(l.get(0).getClass().getName());
    }
}
