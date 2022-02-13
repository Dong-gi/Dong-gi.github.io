import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        var isLocalTest = args.length > 0 && Objects.equals(args[0], "x-local-test");
        var localTestString = "2\r\n"
            + "6\r\n"
            + "22";
        var r = new BufferedReader(isLocalTest ? new StringReader(localTestString) : new InputStreamReader(System.in));
        var w = new BufferedWriter(new OutputStreamWriter(System.out));

        // INIT start
        var max = 40;
        var answer = new int[max + 1][2];

        answer[0][0] = 1;
        answer[1][1] = 1;

        for (int i = 2; i <= max; ++i) {
            answer[i][0] = answer[i - 2][0] + answer[i - 1][0];
            answer[i][1] = answer[i - 2][1] + answer[i - 1][1];
        }
        // INIT end

        var lines = r.lines().collect(Collectors.toList());
        var testCount = Integer.parseInt(lines.get(0));
        for (var i = 1; i <= testCount; ++i) {
            var n = Integer.parseInt(lines.get(i));
            w.append(String.valueOf(answer[n][0])).append(' ').append(String.valueOf(answer[n][1])).append('\n');
        }
        w.flush();
    }
}
