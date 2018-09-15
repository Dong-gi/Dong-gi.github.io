package koreatech;

import java.util.*;

public class Main1043 {

    private static int area = 0;
    private static int max = 0;
    private static char[][] map = null;

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        int width = in.nextInt();
        int height = in.nextInt();
        in.nextLine();

        map = new char[height][width];
        for(int r = 0; r < height; ++r) {
            String line = in.nextLine();
            for(int c = 0; c < width; ++c)
                map[r][c] = line.charAt(c);
        }

        for(int r = 0; r < height; ++r) {
            for(int c = 0; c < width; ++c) {
                check(r, c);
                max = (area > max)? area : max;
                area = 0;
            }
        }

        System.out.println(max);
    }

    private static void check(int row, int col) {
        if(row < 0 || col < 0 || row >= map.length || col >= map[0].length || map[row][col] == '1')
            return;

        boolean isGrass = (map[row][col] == '*'); 
        map[row][col] = '1';

        if(isGrass) {
            area += 1;
            //System.out.printf("row : %d, col : %d, area : %d\n", row, col, area);
            check(row-1, col);
            check(row+1, col);
            check(row, col-1);
            check(row, col+1);
        }
    }
}