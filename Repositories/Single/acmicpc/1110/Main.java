import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        var isLocalTest = args.length > 0 && Objects.equals(args[0], "x-local-test");
        var localTestString = "1";
        var r = new BufferedReader(isLocalTest ? new StringReader(localTestString) : new InputStreamReader(System.in));
        var w = new BufferedWriter(new OutputStreamWriter(System.out));

        var lines = r.lines().collect(Collectors.toList());
        var n = Integer.parseInt(lines.get(0));
        var cycleCount = 1;
        var newNumber = cycle(n);
        while (n != newNumber) {
            cycleCount += 1;
            newNumber = cycle(newNumber);
        }
        w.append(String.valueOf(cycleCount));
        w.flush();
    }

    public static int cycle(int n) { return (n % 10) * 10 + (n / 10 + n % 10) % 10; }
}
