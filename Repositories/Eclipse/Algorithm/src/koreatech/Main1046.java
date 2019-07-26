package koreatech;

import java.util.*;

public class Main1046 {

    private static char[][] map;
    private static boolean reachable;

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        int testCase = in.nextInt();
        while(testCase-- > 0) {
            reachable = false;
            map = new char[in.nextInt()][in.nextInt()];
            ArrayList<Integer[]> next = new ArrayList<>();
            for(int i = 0; i < map.length; ++i) {
                String line = in.next();
                for(int j = 0; j < map[0].length; ++j) {
                    map[i][j] = line.charAt(j);
                    if(map[i][j] == 'E') {
                        next.add(new Integer[] {i, j});
                    }
                }
            }

            int round = 0;
            while(!reachable) {
                ArrayList<Integer[]> cur = next;
                next = new ArrayList<>();
                if(cur.isEmpty()) {
                    break;
                }
                while(!cur.isEmpty()) {
                    Integer[] pos = cur.remove(0);
                    int r = pos[0], c = pos[1];
                    if(mark(r, c+1)) {
                        next.add(new Integer[] {r, c+1});
                    }
                    if(mark(r, c-1)) {
                        next.add(new Integer[] {r, c-1});
                    }
                    if(mark(r+1, c)) {
                        next.add(new Integer[] {r+1, c});
                    }
                    if(mark(r-1, c)) {
                        next.add(new Integer[] {r-1, c});
                    }
                }
                round += 1;
            }
            System.out.println(reachable? round : -1);
        }
    }

    private static boolean mark(int r, int c) {
        try {
            switch(map[r][c]) {
            case 'S': reachable = true; return true;
            case '#': return false;
            case '-': map[r][c] = '.'; return true;
            default: return false;
            }
        } catch(Exception e) { return false; }
    }
}