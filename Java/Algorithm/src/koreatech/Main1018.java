package koreatech;

import java.util.Scanner;
import java.util.Stack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

public class Main1018 {

    private static char[] arr1, arr2;
    private static int distance;
    
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int testCase = scanner.nextInt();
        while(testCase-- > 0) {
            distance = Integer.MAX_VALUE;
            arr1 = scanner.next().toCharArray();
            arr2 = scanner.next().toCharArray();
            
            int size = arr2.length - arr1.length; 
            HashSet<Integer> indexSet = new HashSet<>();
            for(int i = 0; i < arr1.length; ++i) {
                for(int j = i; j <= i + size; ++j) {
                    if(arr1[i] == arr2[j]) {
                        indexSet.add(j-i);
                    }
                }
            }
            if(indexSet.isEmpty()) {
                System.out.println(arr1.length);
                continue;
            }
            for(int from : indexSet.toArray(new Integer[0])) {
                distance(from);
            }
            System.out.println(distance);
        }
    }
    
    private static void distance(int from) {
        int result = 0;
        for(int i = 0; i < arr1.length; ++i) {
            if(arr1[i] != arr2[i+from]) {
                result += 1;
                if(result >= distance) {
                    return;
                }
            }
        }
        if(distance > result) {
            distance = result;
        }
    }

}