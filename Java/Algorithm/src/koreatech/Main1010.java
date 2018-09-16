package koreatech;

import java.util.*;

public class Main1010 {

    private static boolean[] isPrime;

    public static void main(String[] args) throws Exception {
        int size0 = new Scanner(System.in).nextInt();
        int size = (int)Math.pow(10, size0);
        try {
            findPrimesTo(size);
        } catch (Exception e) {
            throw new Exception("original size = " + size0 + ", pow size = " + size);
        }

        Integer[] primes = {2, 3, 5, 7}; 
        primes = getAnswer(primes, size/10);
        for(Integer prime : primes) {
            System.out.println(prime);
        }
    }

    private static void findPrimesTo(int size) {
        isPrime = new boolean[size+1];
        Arrays.fill(isPrime, 2, isPrime.length-1, true);
        for (int i = 3; i*i <= size; i += 2) {
            try {
                if (isPrime[i])
                    for (int j = i; ; j += 2)
                        isPrime[i*j] = false;
            } catch(Exception e) {}
        }
    }

    private static Integer[] getAnswer(Integer[] primes, int from) {
        final int[] validTail = new int[] { 1, 3, 7, 9 };
        ArrayList<Integer> workList = new ArrayList<>();
        ArrayList<Integer> answer = new ArrayList<>();
        Collections.addAll(workList, primes);
        while(!workList.isEmpty()) {
            int current = workList.remove(0);
            if(current > from) {
                answer.add(current);
            } else {
                for(int tail : validTail) {
                    int append = current * 10 + tail;
                    if(isPrime[append]) {
                        workList.add(append);
                    }
                }
            }
        }
        return answer.toArray(new Integer[0]);
    }

}