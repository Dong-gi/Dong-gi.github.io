import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        var isLocalTest = args.length > 0 && Objects.equals(args[0], "x-local-test");
        var localTestString = "1 3 0\r\n"
            + "2 3 0 2 0\r\n"
            + "3 3 0 2 0 2 0\r\n"
            + "3 3 0 2 1 2 3\r\n"
            + "2 4 1 3 4\r\n"
            + "4 5 0 5 1 5 2 5 101\r\n"
            + "0";
        var r = new BufferedReader(isLocalTest ? new StringReader(localTestString) : new InputStreamReader(System.in));
        var w = new BufferedWriter(new OutputStreamWriter(System.out));

        while (true) {
            var parts = r.readLine().split(" ");
            if (parts.length < 2)
                break;
            var numbers = Arrays.stream(parts).skip(1).mapToInt(text -> Integer.parseInt(text)).toArray();
            var count = numbers[0];
            var isMultiply = false;
            for (var i = 1; i < numbers.length; ++i, isMultiply = !isMultiply) {
                if (isMultiply)
                    count *= numbers[i];
                else
                    count -= numbers[i];
            }
            w.append(String.valueOf(count)).append('\n');
        }
        w.flush();
    }
}
