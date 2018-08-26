package io.github.donggi;

public class TypeArray {

    public static void main(String[] args) {
        int[] arr1;
        int arr2[];
        arr1 = new int[10];
        arr2 = new int[] { 1, 2, 3 };

        // JaggedArray
        int[][] arr3 = new int[2][];
        arr3[0] = new int[10];
        arr3[1] = new int[20];

        var arr4 = new int[][] {
            {1, 2, 3},
            {4, 5, 6, 7}
        };
    }

}
