import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        var isLocalTest = args.length > 0 && Objects.equals(args[0], "x-local-test");
        var localTestString = "150\r\n"
            + "266\r\n"
            + "427";
        var r = new BufferedReader(isLocalTest ? new StringReader(localTestString) : new InputStreamReader(System.in));
        var w = new BufferedWriter(new OutputStreamWriter(System.out));

        var counts = new int[10];
        String.valueOf(Integer.parseInt(r.readLine()) * Integer.parseInt(r.readLine()) * Integer.parseInt(r.readLine()))
            .chars().forEach(digit -> counts[digit - '0'] += 1);
        for (var count : counts)
            w.append(String.valueOf(count)).append('\n');
        w.flush();
    }
}
