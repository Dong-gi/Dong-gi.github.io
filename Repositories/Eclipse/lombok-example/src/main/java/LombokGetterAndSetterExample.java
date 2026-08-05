import java.util.Arrays;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public class LombokGetterAndSetterExample {

    @Getter
    @Setter
    static class Rectangle {
        private int x, y;
        @Getter(lazy=true) private final boolean[] isPrime = primes((int)Math.pow(10, 8));
        @Setter(AccessLevel.NONE) private int area;

        public int getArea() {
            return x * y;
        }

        public static boolean[] primes(int size) {
            boolean[] isPrime = new boolean[size+1];
            Arrays.fill(isPrime, 2, isPrime.length-1, true);
            for (int i = 3; i*i <= size; i += 2) {
                try {
                    if (isPrime[i])
                        for (int j = i; ; j += 2)
                            isPrime[i*j] = false;
                } catch(Exception e) {}
            }
            System.out.println("처음 호출될 때 초기화됨.");
            return isPrime;
        }
    }

    public static void main(String[] args) {
        Rectangle r1 = new Rectangle();
        r1.setX(10);
        r1.setY(20);
        System.out.println(r1.getArea());
        System.out.println(r1.getIsPrime()[23333333]);
    }
    /*
200
처음 호출될 때 초기화됨.
false
     */
}