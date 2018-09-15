package koreatech;

import java.util.*;

public class Main1100 {

    public static void main(String[] args) {
        final Scanner in = new Scanner(System.in);
        String n1 = in.next();
        String n2 = in.next();
        System.out.print(Integer.valueOf(n1.replace('6', '5')) + Integer.valueOf(n2.replace('6', '5')));
        System.out.print(' ');
        System.out.print(Integer.valueOf(n1.replace('5', '6')) + Integer.valueOf(n2.replace('5', '6')));
    }

}
