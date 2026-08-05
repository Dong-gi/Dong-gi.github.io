import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        var isLocalTest = args.length > 0 && Objects.equals(args[0], "x-local-test");
        var localTestString = "1000";
        var r = new BufferedReader(isLocalTest ? new StringReader(localTestString) : new InputStreamReader(System.in));
        var w = new BufferedWriter(new OutputStreamWriter(System.out));

        var lines = r.lines().collect(Collectors.toList());
        var n = Integer.parseInt(lines.get(0));
        var count = 0;
        for (var testNum = 1; testNum <= n; ++testNum)
            if (isTargetNumber(testNum))
                count += 1;
        w.append(String.valueOf(count));
        w.flush();
    }

    public static boolean isTargetNumber(int n) {
        if (n < 100)
            return true;
        var chars = String.valueOf(n).toCharArray();
        var size = chars.length;
        var diff = chars[1] - chars[0];
        for (int i = 2; i < size; ++i)
            if (chars[i] - chars[i - 1] != diff)
                return false;
        return true;
    }
}
