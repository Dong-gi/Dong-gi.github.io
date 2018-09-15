package koreatech;

import java.util.*;

public class Main1019 {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int testCase = scanner.nextInt();
        while(testCase-- > 0) {
            String numStr = scanner.next();
            char[] chars = numStr.toCharArray();
            Arrays.parallelSort(chars);

            int bigInt = Integer.parseInt(numStr), smallInt = bigInt;

            for(int i = 0; i < chars.length-1; ++i) {
                if(numStr.charAt(i) == chars[chars.length-1-i]) continue;
                StringBuilder builder = new StringBuilder(numStr);
                builder.setCharAt(numStr.lastIndexOf(chars[chars.length-1-i]), numStr.charAt(i));
                builder.setCharAt(i, chars[chars.length-1-i]);
                bigInt = Integer.parseInt(builder.toString());
                break;
            }

            for(int i = 0, fix = 0; i < chars.length-1; ++i) {
                if(numStr.charAt(i) == chars[fix])
                    fix++;
                else {
                    if(i == 0 && chars[0] == '0') {
                        int _oldFix = fix;
                        while(chars[fix] == '0') fix++;
                        if(chars[fix] >= numStr.charAt(i)) {
                            fix = _oldFix;
                            continue;
                        }
                    }
                    StringBuilder builder = new StringBuilder(numStr);
                    builder.setCharAt(numStr.lastIndexOf(chars[fix]), numStr.charAt(i));
                    builder.setCharAt(i, chars[fix]);
                    smallInt = Integer.parseInt(builder.toString());
                    break;
                }
            }
            //System.out.println(bigInt + " " + smallInt);
            System.out.println(bigInt - smallInt);
        }
    }

}