package koreatech;

import java.util.*;

public class Main1031 {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int testCase = scanner.nextInt();
        while(testCase-- > 0) {
            Stack<Integer> rests = new Stack<>();
            int num = scanner.nextInt();
            int radix = scanner.nextInt();
            while(num > 0) {
                rests.push(num%radix);
                num /= radix;
            }
            StringBuilder builder = new StringBuilder();
            while(!rests.isEmpty()) {
                int rest = rests.pop(); 
                switch(rest) {
                case 10: builder.append('A'); break;
                case 11: builder.append('B'); break;
                case 12: builder.append('C'); break;
                case 13: builder.append('D'); break;
                case 14: builder.append('E'); break;
                case 15: builder.append('F'); break;
                default: builder.append(rest);
                }
            }
            System.out.println(builder.toString());
        }
    }
    
}