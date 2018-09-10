package koreatech;

import java.util.Scanner;

import java.util.HashSet;

public class Main1007 {

    public static void main(String[] args) {
        HashSet<Integer> set = new HashSet<>();
        Scanner scanner = new Scanner(System.in);
        int testCase = scanner.nextInt();
        while(testCase-- > 0) {
            set.clear();
            int numberCount = scanner.nextInt();
            while(numberCount-- > 0) {
                int number = scanner.nextInt();
                boolean _no_use = set.add(number) || set.remove(number);
            }
            System.out.println(set.toArray()[0]);
        }
    }

}