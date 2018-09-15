package koreatech;

import java.util.*;

public class Main1028 {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int testCase = scanner.nextInt();
        while(testCase-- > 0) {
            String str = scanner.next();
            String[] r = str.split("[GB]");
            String[] g = str.split("[RB]");
            String[] b = str.split("[RG]");
            int max = 0;
            for(String s : r) {
                max = max>s.length()? max : s.length();
            }
            for(String s : g) {
                max = max>s.length()? max : s.length();
            }
            for(String s : b) {
                max = max>s.length()? max : s.length();
            }
            System.out.println(str.length() - max);
        }
    }
    
}