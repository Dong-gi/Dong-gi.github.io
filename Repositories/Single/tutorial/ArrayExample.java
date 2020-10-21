public class ArrayExample {
    public static void main(String[] args) {
        int[] arr1;
        int arr2[];
        arr1 = new int[10];
        arr2 = new int[] { 1, 2, 3 };
        int[] arr3 = {1, 2, 3};

        // 자바의 다차원 배열은 기본적으로 Jagged Array
        int[][] arr4 = new int[2][];
        arr4[0] = new int[10];
        arr4[1] = new int[20];

        var arr5 = new int[][] { { 1, 2, 3 }, { 4, 5, 6 } };
        int[][] arr6 = { { 1, 2, 3 }, { 4, 5, 6} };
    }
}