import java.io.*;
import java.util.*;
import java.text.*;
import java.math.*;
import java.util.regex.*;
import java.util.stream.Collectors;
import java.util.*;

interface AdvancedArithmetic{
    int divisorSum(int n);
}
class Calculator implements AdvancedArithmetic {
    public int divisorSum(int n) {
        Set<Integer> set = new HashSet<Integer>();
        if(n % 2 == 0) {
            set.add(2);
            set.add(n / 2);
        }

        for(int i = 1; ; i += 2) {
            if(i * 2 >= n) break;
            if(n % i == 0) {
                set.add(i);
                set.add(n / i);
            }
        }
        return set.stream().mapToInt(x -> x).sum();
    }
}

 class Solution {

     public static void main(String[] args) {
         Scanner scan = new Scanner(System.in);
         int n = scan.nextInt();
         scan.close();

         AdvancedArithmetic myCalculator = new Calculator();
         int sum = myCalculator.divisorSum(n);
         System.out.println("I implemented: " + myCalculator.getClass().getInterfaces()[0].getName() );
         System.out.println(sum);
     }
 }