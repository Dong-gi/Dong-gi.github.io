import java.io.*;
import java.time.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        var isLocalTest = args.length > 0 && Objects.equals(args[0], "x-local-test");
        var localTestString = "9 2";
        var r = new BufferedReader(isLocalTest ? new StringReader(localTestString) : new InputStreamReader(System.in));
        var w = new BufferedWriter(new OutputStreamWriter(System.out));

        var parts = r.readLine().split(" ");
        w.append(LocalDate.of(2007, Month.of(Integer.parseInt(parts[0])), Integer.parseInt(parts[1])).getDayOfWeek()
            .name().substring(0, 3));
        w.flush();
    }
}
