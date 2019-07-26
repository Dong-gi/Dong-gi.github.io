package koreatech;

import java.util.*;

public class Main1074 {

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        int cases = in.nextInt();
        while(cases-- > 0) {
            int size = in.nextInt();
            TreeSet<Integer> set = new TreeSet<>();
            while(size-- > 0) {
                int num = in.nextInt();
                if(!set.add(num)) {
                    set.remove(num);
                }
            }
            Integer[] two = set.toArray(new Integer[0]);
            System.out.format("%d %d%n", two[0], two[1]);
        }
    }
}
