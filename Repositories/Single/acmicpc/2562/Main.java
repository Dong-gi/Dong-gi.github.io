import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        var isLocalTest = args.length > 0 && Objects.equals(args[0], "x-local-test");
        var localTestString = "3\r\n"
            + "29\r\n"
            + "38\r\n"
            + "12\r\n"
            + "57\r\n"
            + "74\r\n"
            + "40\r\n"
            + "85\r\n"
            + "61";
        var r = new BufferedReader(isLocalTest ? new StringReader(localTestString) : new InputStreamReader(System.in));
        var w = new BufferedWriter(new OutputStreamWriter(System.out));

        var nums = r.lines().mapToInt(line -> Integer.parseInt(line)).toArray();
        var maxNum = Arrays.stream(nums).max().getAsInt();
        var nth = -1;
        for (var i = 0; nth < 0 && i < nums.length; ++i)
            if (nums[i] == maxNum)
                nth = i + 1;

        w.append(String.valueOf(maxNum)).append('\n').append(String.valueOf(nth));
        w.flush();
    }
}
