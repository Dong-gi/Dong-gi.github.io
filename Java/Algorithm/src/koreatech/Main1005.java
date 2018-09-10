package koreatech;

import java.util.Scanner;

import java.util.ArrayList;
import java.util.HashSet;

public class Main1005 {

    private static HashSet<Integer> one = new HashSet<>();
    private static HashSet<Integer> two = new HashSet<>();
    private static HashSet<Integer> three = new HashSet<>();
    private static ArrayList<Integer> numbers = new ArrayList<>();

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int numberCount = scanner.nextInt();
        while(numberCount-- > 0) {
            int current = scanner.nextInt();
            boolean _no_use = one.add(current) || two.add(current) ||three.add(current);
            numbers.add(current);
        }
        numbers.sort((arg0, arg1) -> arg0 - arg1);

        int firstLimit = numbers.size() - 2; // {>0<, 0, 0}
        int secondLimit = numbers.size() - 1; // {0, >0<, 0}
        int count = 0;
        for(int i = 0; i < firstLimit; i++) {
            int small = numbers.get(i);
            if(small > 0) break;

            for(int j = i+1; j < secondLimit; j++) {
                int center = numbers.get(j);
                int big = -(small + center);

                if(big >= center) {
                    //System.out.printf("test : %d, %d, %d \n", small, center, big);
                    boolean result = (small == center)?
                            ((center == big)? three.contains(big) : one.contains(big)) :
                                ((center == big)? two.contains(big) : one.contains(big));
                    if(result) {
                        //System.out.printf("find : %d, %d, %d \n", small, center, big);
                        count++;
                    }
                }
                try {
                    while(numbers.get(j+1) == center) j++;
                } catch(Exception e) {}
            }

            try {
                while(numbers.get(i+1) == small) i++;
            } catch(Exception e) {}
        }
        
        System.out.println(count);
    }

}