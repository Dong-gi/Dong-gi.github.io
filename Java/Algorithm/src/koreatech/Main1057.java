package koreatech;

import java.util.*;

public class Main1057 {

    public static void main(String[] args) {
        final Scanner in = new Scanner(System.in);
        final int numOfTestCase = Integer.parseInt(in.nextLine().trim());

        // results[3][n] : n으로 끝나는 3자리 전화번호
        long[][] results = new long[101][10];
        Arrays.fill(results[1], 1);
        for(int i = 2; i <= 100; ++i) {
             results[i][0] = results[i-1][8];
             results[i][1] = results[i-1][2] + results[i-1][4];
             results[i][2] = results[i-1][1] + results[i-1][3] + results[i-1][5];
             results[i][3] = results[i-1][2] + results[i-1][6];
             results[i][4] = results[i-1][1] + results[i-1][5] + results[i-1][7];
             results[i][5] = results[i-1][2] + results[i-1][4] + results[i-1][6] + results[i-1][8];
             results[i][6] = results[i-1][3] + results[i-1][5] + results[i-1][9];
             results[i][7] = results[i-1][4] + results[i-1][8];
             results[i][8] = results[i-1][5] + results[i-1][7] + results[i-1][9] + results[i-1][0];
             results[i][9] = results[i-1][6] + results[i-1][8];
             for(int j = 1; j < 10; ++j)
                 results[i][j] %= 1_000_000_007;
        }
        
        for (int testCase = 0; testCase < numOfTestCase; ++testCase) {
            long sum = 0;
            for(long n : results[Integer.parseInt(in.nextLine().trim())])
                sum += n;
            System.out.println(sum % 1_000_000_007);
        }
    }
}