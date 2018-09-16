package koreatech;

import java.util.*;

public class Main1063 {

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        int testCase = in.nextInt();
        while(testCase-- > 0) {
            int num = in.nextInt();
            int max = num/2;
            int count = 1; // 1만 쓰는 경우
            for(int i = 1; i <= max; ++i) {
                int n = num - 2*i + 1; // 2가 들어갈 수 있는 자리들
                // map[i][j] : i번째 위치까지 2를 j개 놓은 경우의 수
                // 예. map[4][2] : 총 4개 위치에 2를 2개 넣는 경우의 수
                // 1 1 1
                // 1 2 3 // map[i][j] = sum(map[i-1][k]), k in [0, j]
                // 1 3 6 // map[i][j] = map[i][j-1] + map[i-1][j]
                // 1 4 10
                int[][] map = new int[n][i+1];
                Arrays.fill(map[0], 1);
                for(int r = 1; r < n; ++r) {
                    map[r][0] = 1;
                    for(int c = 1; c <= i; ++c) {
                        map[r][c] = map[r][c-1] + map[r-1][c];
                    }
                }
                count += map[n-1][i];
            }
            System.out.println(count);
        }
    }
}
