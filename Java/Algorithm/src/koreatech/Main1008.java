package koreatech;

import java.util.Scanner;
import java.util.ArrayList;

public class Main1008 {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int testCase = scanner.nextInt();
        while(testCase-- > 0){
            int a = scanner.nextInt();
            int b = scanner.nextInt();
            System.out.print(a/b+".");
            
            ArrayList<Integer> remains = new ArrayList<Integer>();
            while(true){
                a = (a%b)*10;
                if(remains.contains(a)) {
                    break;
                }
                remains.add(a);
            }
            
            int startIdx = remains.indexOf(a);
            for(int i=0; i<startIdx; ++i) {
                System.out.print(remains.get(i)/b);
            }
            System.out.print("(");
            
            int endIdx = remains.size()-1;
            for(int i=startIdx; i<=endIdx; ++i) {
                System.out.print(remains.get(i)/b);
            }
            System.out.println(")");
        }
    }

}