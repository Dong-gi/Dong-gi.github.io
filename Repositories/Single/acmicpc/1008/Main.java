import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        var isLocalTest = args.length > 0 && Objects.equals(args[0], "x-local-test");
        var localTestString = "1 3";
        var r = new BufferedReader(isLocalTest ? new StringReader(localTestString) : new InputStreamReader(System.in));
        var w = new BufferedWriter(new OutputStreamWriter(System.out));

        var lines = r.lines().collect(Collectors.toList());
        var parts = lines.get(0).split(" ");
        var a = (double) Integer.parseInt(parts[0]);
        var b = Integer.parseInt(parts[1]);
        w.append("%.11f".formatted(a / b)).append('\n');
        w.flush();
    }
}
