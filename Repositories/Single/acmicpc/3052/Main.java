import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        var isLocalTest = args.length > 0 && Objects.equals(args[0], "x-local-test");
        var localTestString = "39\r\n"
            + "40\r\n"
            + "41\r\n"
            + "42\r\n"
            + "43\r\n"
            + "44\r\n"
            + "82\r\n"
            + "83\r\n"
            + "84\r\n"
            + "85";
        var r = new BufferedReader(isLocalTest ? new StringReader(localTestString) : new InputStreamReader(System.in));
        var w = new BufferedWriter(new OutputStreamWriter(System.out));

        w.append(String.valueOf(r.lines().map(line -> Integer.parseInt(line) % 42).collect(Collectors.toSet()).size()));
        w.flush();
    }
}
