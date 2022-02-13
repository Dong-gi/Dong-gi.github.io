import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        var isLocalTest = args.length > 0 && Objects.equals(args[0], "x-local-test");
        var localTestString = "120\r\n"
            + "5611\r\n"
            + "100\r\n"
            + "0";
        var r = new BufferedReader(isLocalTest ? new StringReader(localTestString) : new InputStreamReader(System.in));
        var w = new BufferedWriter(new OutputStreamWriter(System.out));

        var lines = r.lines().collect(Collectors.toList());
        var limit = lines.size() - 1;

        for (var i = 0; i < limit; ++i) {
            var size = 1;
            for (var ch : lines.get(i).toCharArray()) {
                switch (ch) {
                    case '0':
                        size += 5;
                        break;
                    case '1':
                        size += 3;
                        break;
                    default:
                        size += 4;
                        break;
                }
            }
            w.append(String.valueOf(size)).append('\n');
        }
        w.flush();
    }
}
