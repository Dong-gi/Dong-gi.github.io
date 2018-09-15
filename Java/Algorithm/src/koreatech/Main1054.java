package koreatech;

import java.util.*;

public class Main1054 {

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int num = Integer.parseInt(sc.nextLine().trim());
        
        String str1 = sc.nextLine();
        for(int i=0; i<num-1; i++) {
            String str2 = sc.nextLine();
            if(str1.length() > str2.length())
                str1 = str2;
        }
        
        System.out.println(str1);
    }
}