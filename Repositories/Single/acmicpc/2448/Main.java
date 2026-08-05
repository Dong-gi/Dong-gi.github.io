import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        var isLocalTest = args.length > 0 && Objects.equals(args[0], "x-local-test");
        var localTestString = "24";
        var r = new BufferedReader(isLocalTest ? new StringReader(localTestString) : new InputStreamReader(System.in));
        var w = new BufferedWriter(new OutputStreamWriter(System.out));

        var size = Integer.parseInt(r.readLine());
        var lines = new StringBuilder[size + 1];
        lines[1] = new StringBuilder("*");
        lines[2] = new StringBuilder("* *");
        lines[3] = new StringBuilder("*****");

        for (var processedLineCount = 3; processedLineCount < size; processedLineCount *= 2) {
            for (var baseLineIdx = 1; baseLineIdx <= processedLineCount; ++baseLineIdx) {
                var blanks = new char[(processedLineCount - baseLineIdx) * 2 + 1];
                Arrays.fill(blanks, ' ');
                lines[baseLineIdx + processedLineCount] = new StringBuilder(lines[baseLineIdx]).append(blanks)
                    .append(lines[baseLineIdx]);
            }
        }
        for (var i = 1; i <= size; ++i) {
            var blanks = new char[size - i];
            Arrays.fill(blanks, ' ');
            var blankStr = new String(blanks);
            w.append(blankStr).append(lines[i].toString()).append(blankStr).append('\n');
        }
        w.flush();
    }
}
