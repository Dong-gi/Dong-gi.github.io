package koreatech;

import java.util.*;

public class Main1047 {

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        int size = in.nextInt();
        HashSet<Integer> set = new HashSet<Integer>();
        while(size-- > 0)
            set.add(in.nextInt());
        System.out.println(set.size());
    }
}