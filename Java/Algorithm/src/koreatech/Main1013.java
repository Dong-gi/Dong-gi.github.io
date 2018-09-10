package koreatech;

import java.util.Scanner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

public class Main1013 {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int testCase = scanner.nextInt();
        while(testCase-- > 0) {
            int length = scanner.nextInt();
            int[] sales = new int[length];
            HashSet<Integer> saleSet = new HashSet<Integer>();
            for(int i = 0; i < length; ++i) {
                saleSet.add(sales[i] = scanner.nextInt());
            }

            Integer[] saleValues = saleSet.toArray(new Integer[0]);
            Arrays.sort(saleValues);
            ArrayList<Integer[]> progressList = new ArrayList<>();
            for(int value : saleValues) {
                progressList.add(new Integer[] {value, 0});
            }

            int count = 0;
            for(int sale : sales) {
                for(Integer[] item : progressList) {
                    /* progressList는 현재까지 등장한 sale값들의 수를 갖는다.
                     * progressList는 정렬되어 있으므로,
                     * 현재 sale값을 만날 때까지 등장한 날들의 총합이 향상일수이다. 
                     */
                    if(item[0] == sale) {
                        count += item[1]++;
                        break;
                    }
                    count += item[1];
                }
            }

            System.out.println(count);
        }
    }

}