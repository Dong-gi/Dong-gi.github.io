import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        var isLocalTest = args.length > 0 && Objects.equals(args[0], "x-local-test");
        var localTestString = "3\r\n"
            + "0 3\r\n"
            + "1 5\r\n"
            + "45 50";
        var r = new BufferedReader(isLocalTest ? new StringReader(localTestString) : new InputStreamReader(System.in));
        var w = new BufferedWriter(new OutputStreamWriter(System.out));

        var lines = r.lines().collect(Collectors.toList());
        var testCount = Integer.parseInt(lines.get(0));
        for (var testNum = 1; testNum <= testCount; ++testNum) {
            var parts = lines.get(testNum).split(" ");
            var x = Integer.parseInt(parts[0]);
            var y = Integer.parseInt(parts[1]);

            var distance = y - x;
            var n = (int) Math.floor(Math.sqrt(distance));
            distance -= n * n;
            var moveCount = 2 * n - 1 + distance / n + (distance % n == 0 ? 0 : 1);
            w.append(String.valueOf(moveCount)).append('\n');
        }
        w.flush();
    }
}
