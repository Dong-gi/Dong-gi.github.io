package koreatech;

import java.util.Scanner;

public class Main1093 {

    public static void main(String[] args) {
        final Scanner in = new Scanner(System.in);
        final int numOfTestCase = Integer.parseInt(in.nextLine().trim());

        for (int testCase = 0; testCase < numOfTestCase; ++testCase) {
            int n = in.nextInt();
            int k = in.nextInt();
            int result = -1;
            // n = result + (k+1) * x + 1

            if (n == 1) {
            } else if (n <= k + 1) {
                result = n - 1;
            } else {
                result = (n - 1) % (k + 1);
                if (result == 0)
                    result = -1;
            }
            System.out.println(result);
        }
    }

}
