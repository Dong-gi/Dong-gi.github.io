import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        var isLocalTest = args.length > 0 && Objects.equals(args[0], "x-local-test");
        var localTestString = "Mississipi";
        var r = new BufferedReader(isLocalTest ? new StringReader(localTestString) : new InputStreamReader(System.in));
        var w = new BufferedWriter(new OutputStreamWriter(System.out));

        var charCount = new int[] {
            0, 0, 0, 0, 0,
            0, 0, 0, 0, 0,
            0, 0, 0, 0, 0,
            0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0
        };

        var lines = r.lines().collect(Collectors.toList());
        char modeChar = 0;
        var modeCount = 0;
        for (var ch : lines.get(0).toUpperCase().toCharArray()) {
            var idx = ch - 'A';
            charCount[idx] += 1;
            if (modeCount < charCount[idx]) {
                modeCount = charCount[idx];
                modeChar = ch;
            } else if (modeCount == charCount[idx])
                modeChar = '?';
        }
        w.append(modeChar);
        w.flush();
    }
}
