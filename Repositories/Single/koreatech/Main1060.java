package koreatech;

import java.util.*;

public class Main1060 {

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        char[] cs = in.next().toCharArray();
        // 만들 수 있는 최대 길이 팰린드롬 찾기
        // cs[i] : 0부터 오른쪽으로 한 글자씩 탐색
        // cs[j] : cs.length-1부터 왼쪽으로 한 글자씩 탐색
        // pal[i+1][j+1] : cs[i], cs[j]를 탐색하면서 발견한 최대 팰린드롬 길이
        int[][] pal = new int[cs.length+2][cs.length+2];
        for(int i = 0; i < cs.length; ++i) {
            for(int j = cs.length-1; j >= 0; --j) {
                if(cs[i] == cs[j]) {
                    // cs[i-1], cs[j+1]에서의 최대 팰린드롬 길이 +1
                    pal[i+1][j+1] = pal[i][j+2] + 1;
                } else {
                    // cs[i], cs[j+1] or cs[i-1], cs[j]
                    pal[i+1][j+1] = Math.max(pal[i+1][j+2], pal[i][j+1]);
                }
            }
        }
        System.out.println(cs.length - pal[cs.length][1]);
    }
}
