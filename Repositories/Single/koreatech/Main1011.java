import java.util.*;

class Main1011 {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int testCase = scanner.nextInt();
        while(testCase-- > 0) {
            int length = scanner.nextInt();
            int[] price = new int[length];
            for(int i = 0; i < length; ++i) {
                price[i] = scanner.nextInt();
            }

            int[] mins = new int[length];
            for(int i = 0; i < length; ++i) {
                int[] beforeMins = new int[3];
                beforeMins[0] = (i-3 >= 0)? mins[i-3] : 0;
                beforeMins[1] = (i-2 >= 0)? mins[i-2] : 0;
                beforeMins[2] = (i-1 >= 0)? mins[i-1] : 0;
                Arrays.sort(beforeMins);
                mins[i] = beforeMins[0] + price[i];
            }

            int[] result = new int[3];
            result[0] = (length-3 >= 0)? mins[length-3] : 0;
            result[1] = (length-2 >= 0)? mins[length-2] : 0;
            result[2] = (length-1 >= 0)? mins[length-1] : 0;
            Arrays.sort(result);
            System.out.println(result[0]);
        }
    }
}