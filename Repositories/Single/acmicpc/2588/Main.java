import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        var isLocalTest = args.length > 0 && Objects.equals(args[0], "x-local-test");
        var localTestString = "472\r\n"
            + "385";
        var r = new BufferedReader(isLocalTest ? new StringReader(localTestString) : new InputStreamReader(System.in));
        var w = new BufferedWriter(new OutputStreamWriter(System.out));

        var num1 = Integer.parseInt(r.readLine());
        var num2 = Integer.parseInt(r.readLine());

        var num2digit1 = num2 % 10;
        var num3 = num1 * num2digit1;

        var num2digit10 = (num2 % 100) / 10;
        var num4 = num1 * num2digit10;

        var num2digit100 = num2 / 100;
        var num5 = num1 * num2digit100;

        var num6 = num1 * num2;
        w.append(String.valueOf(num3)).append('\n');
        w.append(String.valueOf(num4)).append('\n');
        w.append(String.valueOf(num5)).append('\n');
        w.append(String.valueOf(num6)).append('\n');
        w.flush();
    }
}
