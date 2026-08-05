import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        var isLocalTest = args.length > 0 && Objects.equals(args[0], "x-local-test");
        var localTestString = "3\r\n"
            + "0 0 13 40 0 37\r\n"
            + "0 0 3 0 7 4\r\n"
            + "1 1 1 1 1 5";
        var r = new BufferedReader(isLocalTest ? new StringReader(localTestString) : new InputStreamReader(System.in));
        var w = new BufferedWriter(new OutputStreamWriter(System.out));

        var lines = r.lines().collect(Collectors.toList());
        var testCount = Integer.parseInt(lines.get(0));
        var eps = 1.e-6;
        for (var i = 1; i <= testCount; ++i) {
            var parts = lines.get(i).split(" ");
            var x1 = Integer.parseInt(parts[0]);
            var y1 = Integer.parseInt(parts[1]);
            var r1 = Integer.parseInt(parts[2]);
            var x2 = Integer.parseInt(parts[3]);
            var y2 = Integer.parseInt(parts[4]);
            var r2 = Integer.parseInt(parts[5]);

            // 두 원이 동일
            if (x1 == x2 && y1 == y2 && r1 == r2)
                w.append("-1").append('\n');
            else {
                var dist = Math.sqrt(Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2));
                if (Math.abs(dist - r1 - r2) < eps ||
                    Math.abs(dist + r1 - r2) < eps ||
                    Math.abs(dist + r2 - r1) < eps)
                    w.append("1").append('\n');
                else if (dist > r1 + r2 ||
                    dist + r1 < r2 ||
                    dist + r2 < r1)
                    w.append("0").append('\n');
                else
                    w.append("2").append('\n');
            }
        }
        w.flush();
    }
}
