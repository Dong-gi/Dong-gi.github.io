package link4.joy.koreatech;

import java.util.*;

class Main1018 {
    public static void main(String[] args) {
        @SuppressWarnings("resource")
        Scanner scanner = new Scanner(System.in);
        int testCase = scanner.nextInt();
        while (testCase-- > 0) {
            char[] arr1 = scanner.next().toCharArray();
            char[] arr2 = scanner.next().toCharArray();

            int[] index = new int[arr2.length - arr1.length + 1];
            for (int i = 0; i < arr1.length; ++i)
                for (int j = i; j < i + index.length; ++j)
                    if (arr1[i] == arr2[j])
                        index[j - i]++;

            Arrays.sort(index);
            System.out.println(arr1.length - index[index.length - 1]);
        }
    }
}