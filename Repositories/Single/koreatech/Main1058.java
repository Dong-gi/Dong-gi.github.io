import java.util.*;

class Main1058 {
    public static void main(String[] args) {
        final Scanner in = new Scanner(System.in);
        final int numOfTestCase = Integer.parseInt(in.nextLine().trim());

        for (int testCase = 0; testCase < numOfTestCase; ++testCase) {
            final int[] coins =  new int[in.nextInt()];
            for(int i = 0; i < coins.length; ++i)
                coins[i] = in.nextInt();
            Arrays.parallelSort(coins);

            final int cost = in.nextInt();
            final int[][] cases = new int[cost+1][cost/coins[0]+2];
            // cases[1000][30] : 동전 30개로 1000을 만드는 경우의 수

            cases[0][0] = 1; // 기저 : 0개의 동전으로 0을 만드는 경우의 수

            for(int coin : coins)
                for(int n = cost; n >= coin; --n)
                    for(int i = 1; i < cases[0].length; ++i)
                        for(int j = 1; ; ++j)
                            try {
                                cases[n][i] += cases[n-coin*j][i-1];
                                cases[n][i] %= 1_000_000_007;
                            } catch(Exception e) { break; }

            long sum = 0;
            for(int n : cases[cost])
                sum += n;
            System.out.println(sum % 1_000_000_007);
        }
    }
}