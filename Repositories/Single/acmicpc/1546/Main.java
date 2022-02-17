import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        var isLocalTest = args.length > 0 && Objects.equals(args[0], "x-local-test");
        var localTestString = "3\r\n"
            + "10 20 30";
        var r = new BufferedReader(isLocalTest ? new StringReader(localTestString) : new InputStreamReader(System.in));
        var w = new BufferedWriter(new OutputStreamWriter(System.out));

        r.readLine();
        var scores = Arrays.stream(r.readLine().split(" ")).mapToInt(str -> Integer.parseInt(str)).toArray();
        var sum = Arrays.stream(scores).sum();
        var maxScore = Arrays.stream(scores).max().getAsInt();
        w.append(String.valueOf(sum * 100f / maxScore / scores.length));
        w.flush();
    }
}
