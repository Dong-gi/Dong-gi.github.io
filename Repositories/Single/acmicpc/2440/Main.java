import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        var isLocalTest = args.length > 0 && Objects.equals(args[0], "x-local-test");
        var localTestString = "5";
        var r = new BufferedReader(isLocalTest ? new StringReader(localTestString) : new InputStreamReader(System.in));
        var w = new BufferedWriter(new OutputStreamWriter(System.out));

        var size = Integer.parseInt(r.readLine());
        for (var i = size; i >= 1; --i) {
            for (var j = 1; j <= i; ++j)
                w.append('*');
            w.append('\n');
        }
        w.flush();
    }
}
