import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        var isLocalTest = args.length > 0 && Objects.equals(args[0], "x-local-test");
        var localTestString = "4\r\n"
            + "aba\r\n"
            + "abab\r\n"
            + "abcabc\r\n"
            + "a";
        var r = new BufferedReader(isLocalTest ? new StringReader(localTestString) : new InputStreamReader(System.in));
        var w = new BufferedWriter(new OutputStreamWriter(System.out));

        var lines = r.lines().collect(Collectors.toList());
        var testCount = Integer.parseInt(lines.get(0));
        var count = 0;
        for (var i = 1; i <= testCount; ++i) {
            var charFound = new boolean[26];
            count += 1;

            var chars = lines.get(i).toCharArray();
            var beforeChar = (char) -1;
            for (var idx = 0; idx < chars.length; ++idx) {
                var ch = chars[idx];
                if (beforeChar == ch)
                    continue;
                beforeChar = ch;

                var charFoundIdx = ch - 'a';
                if (charFound[charFoundIdx]) {
                    count -= 1;
                    break;
                }
                charFound[charFoundIdx] = true;
            }
        }
        w.append(String.valueOf(count)).append('\n');
        w.flush();
    }
}
