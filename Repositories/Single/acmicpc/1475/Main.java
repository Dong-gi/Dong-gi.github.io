import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        var isLocalTest = args.length > 0 && Objects.equals(args[0], "x-local-test");
        var localTestString = "12635";
        var r = new BufferedReader(isLocalTest ? new StringReader(localTestString) : new InputStreamReader(System.in));
        var w = new BufferedWriter(new OutputStreamWriter(System.out));

        var numCounts = new int[9];
        for (var ch : r.readLine().toCharArray()) {
            var digit = ch - '0';
            if (digit == 9)
                digit = 6;
            numCounts[digit] += 1;
        }

        var setCount = 0;
        for (var digit = 0; digit < 9; ++digit) {
            var needSetCount = (digit == 6) ? (numCounts[digit] + 1) / 2 : numCounts[digit];
            if (setCount < needSetCount)
                setCount = needSetCount;
        }
        w.append(String.valueOf(setCount));
        w.flush();
    }
}
