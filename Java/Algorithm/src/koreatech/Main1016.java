package koreatech;

import java.util.*;

public class Main1016 {

    private static int[] primes;
    
    public static void main(String[] args) {
        findPrimesTo(10000);
        
        int[][] sums = new int[10001][67];
        // sums[1000][30] : 서로 다른 30개의 소수로 1000을 만드는 경우의 수
        // 최대 66개 연속으로 더하여 9854를 만듦.
        
        sums[0][0] = 1; // 기저 : 0개의 소수로 0을 만드는 경우의 수
        
        for(int prime : primes)
            for(int n = 10000; n >= prime; --n) 
                for(int k = 1; k <= 66; ++k)
                    sums[n][k] = (sums[n][k] + sums[n-prime][k-1]) % 123456789;
        
        Scanner scanner = new Scanner(System.in);
        int testCase = scanner.nextInt();
        while(testCase-- > 0) {
            int result = 0;
            for(int sum : sums[scanner.nextInt()])
                result = (result + sum) % 123456789;
            System.out.println(result);
        }
    }

    private static void findPrimesTo(int size) {
        boolean[] isPrime = new boolean[size+1];
        Arrays.fill(isPrime, 2, isPrime.length-1, true);
        for (int i = 3; i*i <= size; i += 2) {
            try {
                if (isPrime[i]) {
                    for (int j = i; ; j += 2)
                        isPrime[i*j] = false;
                }
            } catch(Exception e) {}
        }
        primes = new int[size/2];
        primes[0] = 2;
        size = 1;
        try {
            for(int i=3; ; i += 2)
                if(isPrime[i]) primes[size++] = i;
        }catch(Exception e) {}
        primes = Arrays.copyOf(primes, size);
    }

}