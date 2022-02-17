import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        var isLocalTest = args.length > 0 && Objects.equals(args[0], "x-local-test");
        var localTestString = "4\r\n"
            + "10 12 10 12\r\n"
            + "10 12 3 9\r\n"
            + "10 12 7 2\r\n"
            + "13 11 5 6";
        var r = new BufferedReader(isLocalTest ? new StringReader(localTestString) : new InputStreamReader(System.in));
        var w = new BufferedWriter(new OutputStreamWriter(System.out));

        var testSize = Integer.parseInt(r.readLine());
        for (var testNum = 0; testNum < testSize; ++testNum) {
            var parts = r.readLine().split(" ");
            var m = Integer.parseInt(parts[0]);
            var n = Integer.parseInt(parts[1]);
            var x = Integer.parseInt(parts[2]) % m;
            var y = Integer.parseInt(parts[3]) % n;

            var visitNum = Math.min(x, y);
            var maxVisitNum = m * n;
            var isFoundX = visitNum == x;
            var isFoundY = visitNum == y;
            if (x == 0 && y == 0)
                if (m < n)
                    isFoundX = false;
                else
                    isFoundY = false;
            var visitDistance = isFoundX ? m : n;

            while (!isFoundX || !isFoundY) {
                if (visitNum > maxVisitNum)
                    break;
                visitNum += visitDistance;
                if (!isFoundX)
                    isFoundX = visitNum % m == x;
                if (!isFoundY)
                    isFoundY = visitNum % n == y;
            }
            if (isFoundX && isFoundY)
                w.append(String.valueOf(visitNum)).append('\n');
            else
                w.append("-1\n");
        }
        w.flush();
    }
}
