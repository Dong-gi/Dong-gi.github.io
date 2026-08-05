import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        var isLocalTest = args.length > 0 && Objects.equals(args[0], "x-local-test");
        var localTestString = "121\r\n"
            + "1231\r\n"
            + "12421\r\n"
            + "0";
        var r = new BufferedReader(isLocalTest ? new StringReader(localTestString) : new InputStreamReader(System.in));
        var w = new BufferedWriter(new OutputStreamWriter(System.out));

        var lines = r.lines().collect(Collectors.toList());
        var limit = lines.size() - 1;
        for (var i = 0; i < limit; ++i) {
            var flag = Objects.equals(lines.get(i), new StringBuilder(lines.get(i)).reverse().toString());
            w.append(flag ? "yes" : "no").append('\n');
        }
        w.flush();
    }
}
