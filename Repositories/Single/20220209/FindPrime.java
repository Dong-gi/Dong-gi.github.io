import java.util.Arrays;

public class FindPrime {
    private static final int FIND_PRIME_UPPER_BOUND = 10_000_000;
    private static final boolean[] IS_PRIME = new boolean[FIND_PRIME_UPPER_BOUND + 1];

    public static void main(String[] args) {
        findPrimes();
        System.out.println(Arrays.toString(getPrimes(100)));
    }

    private static void findPrimes() {
        Arrays.fill(IS_PRIME, 2, FIND_PRIME_UPPER_BOUND, true);
        for (var i = 3; i * i <= FIND_PRIME_UPPER_BOUND; i += 2) {
            if (IS_PRIME[i]) {
                for (int j = i; i * j <= FIND_PRIME_UPPER_BOUND; j += 2)
                    IS_PRIME[i * j] = false;
            }
        }
    }

    private static int[] getPrimes(int size) {
        var primes = new int[size];
        primes[0] = 2;
        var idx = 1;
        for (int i = 3; idx < size && i <= FIND_PRIME_UPPER_BOUND; i += 2)
            if (IS_PRIME[i])
                primes[idx++] = i;
        return primes;
    }
}
