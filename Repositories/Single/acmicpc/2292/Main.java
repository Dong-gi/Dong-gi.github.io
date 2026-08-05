import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        var isLocalTest = args.length > 0 && Objects.equals(args[0], "x-local-test");
        var localTestString = "1000000000";
        var r = new BufferedReader(isLocalTest ? new StringReader(localTestString) : new InputStreamReader(System.in));
        var w = new BufferedWriter(new OutputStreamWriter(System.out));

        var num = Integer.parseInt(r.readLine()) - 1;
        var distance = 1;
        while (num > 0) {
            num -= distance * 6;
            distance += 1;
        }
        w.append(String.valueOf(distance));
        w.flush();
    }
}
