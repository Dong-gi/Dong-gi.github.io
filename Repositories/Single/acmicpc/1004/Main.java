import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        var isLocalTest = args.length > 0 && Objects.equals(args[0], "x-local-test");
        var localTestString = "3\r\n"
            + "-5 1 5 1\r\n"
            + "3\r\n"
            + "0 0 2\r\n"
            + "-6 1 2\r\n"
            + "6 2 2\r\n"
            + "2 3 13 2\r\n"
            + "8\r\n"
            + "-3 -1 1\r\n"
            + "2 2 3\r\n"
            + "2 3 1\r\n"
            + "0 1 7\r\n"
            + "-4 5 1\r\n"
            + "12 1 1\r\n"
            + "12 1 2\r\n"
            + "12 1 3\r\n"
            + "102 16 19 -108\r\n"
            + "12\r\n"
            + "-107 175 135\r\n"
            + "-38 -115 42\r\n"
            + "140 23 70\r\n"
            + "148 -2 39\r\n"
            + "-198 -49 89\r\n"
            + "172 -151 39\r\n"
            + "-179 -52 43\r\n"
            + "148 42 150\r\n"
            + "176 0 10\r\n"
            + "153 68 120\r\n"
            + "-56 109 16\r\n"
            + "-187 -174 8";
        var r = new BufferedReader(isLocalTest ? new StringReader(localTestString) : new InputStreamReader(System.in));
        var w = new BufferedWriter(new OutputStreamWriter(System.out));

        var lines = r.lines().collect(Collectors.toList());
        var testCount = Integer.parseInt(lines.get(0));
        var lineNum = 1;
        for (var i = 1; i <= testCount; ++i) {
            var parts = lines.get(lineNum++).split(" ");
            var x1 = Integer.parseInt(parts[0]);
            var y1 = Integer.parseInt(parts[1]);
            var x2 = Integer.parseInt(parts[2]);
            var y2 = Integer.parseInt(parts[3]);

            var circleCount = Integer.parseInt(lines.get(lineNum++));
            var passCount = 0;
            for (var i2 = 0; i2 < circleCount; ++i2) {
                parts = lines.get(lineNum++).split(" ");
                var cx = Integer.parseInt(parts[0]);
                var cy = Integer.parseInt(parts[1]);
                var cr = Integer.parseInt(parts[2]);

                var dist1 = Math.sqrt(Math.pow(cx - x1, 2) + Math.pow(cy - y1, 2));
                var dist2 = Math.sqrt(Math.pow(cx - x2, 2) + Math.pow(cy - y2, 2));
                var flag1 = dist1 < cr;
                var flag2 = dist2 < cr;
                if (flag1 && flag2)
                    continue;
                else if (flag1 || flag2)
                    passCount += 1;
            }
            w.append(String.valueOf(passCount)).append('\n');
        }
        w.flush();
    }
}
