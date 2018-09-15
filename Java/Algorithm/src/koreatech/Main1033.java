package koreatech;

import java.util.*;

public class Main1033 {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int num = scanner.nextInt();
        if(num == 2 || num == 3 || num == 5 || num == 7) {
            System.out.println("prime");
            return;
        }
        if(num < 2 || num%2 == 0) {
            System.out.println("not prime");
            return;
        }
        int limit = (int)Math.sqrt(num);
        int factor = 3;
        while(factor <= limit) {
            if(num%factor == 0) {
                System.out.println("not prime");
                return;
            }
            factor += 2;
        }
        System.out.println("prime");
    }
    
}