import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        var isLocalTest = args.length > 0 && Objects.equals(args[0], "x-local-test");
        var localTestString = "100 100\r\n"
            + "F 10\r\n"
            + "F 10\r\n"
            + "E 20\r\n"
            + "# 0\r\n"
            + "50 30\r\n"
            + "F 5\r\n"
            + "E 20\r\n"
            + "# 0\r\n"
            + "0 0";
        var r = new BufferedReader(isLocalTest ? new StringReader(localTestString) : new InputStreamReader(System.in));
        var w = new BufferedWriter(new OutputStreamWriter(System.out));

        var lines = r.lines().collect(Collectors.toList());
        var lineIdx = 0;
        var testNum = 0;
        while (true) {
            var line = lines.get(lineIdx++);
            if (Objects.equals("0 0", line))
                break;

            testNum += 1;
            var parts = line.split(" ");
            var optimalW = Integer.parseInt(parts[0]);
            var realW = Integer.parseInt(parts[1]);
            var isDead = false;

            for (line = lines.get(lineIdx++); Objects.equals("# 0", line) == false; line = lines.get(lineIdx++)) {
                if (isDead)
                    continue;
                parts = line.split(" ");
                switch (parts[0].charAt(0)) {
                    case 'E':
                        realW -= Integer.parseInt(parts[1]);
                        break;
                    case 'F':
                        realW += Integer.parseInt(parts[1]);
                        break;
                }
                if (realW <= 0)
                    isDead = true;
            }

            w.append(String.valueOf(testNum)).append(' ');
            if (isDead)
                w.append("RIP\n");
            else if (optimalW / 2 < realW && realW < optimalW * 2)
                w.append(":-)\n");
            else
                w.append(":-(\n");
        }
        w.flush();
    }
}
