package koreatech;

import java.util.Scanner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

public class Main1014 {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int testCase = scanner.nextInt();
        Integer[] start = {0, 0, 0, 0};
        while(testCase-- > 0) {
            int length = scanner.nextInt();
            Integer[][] passwords = new Integer[length][4];
            int nextDistance = Integer.MAX_VALUE;
            int nextIdx = 0;
            for(int i = 0; i < length; ++i) {
                String password = scanner.next();
                passwords[i][0] = password.charAt(0) - '0';
                passwords[i][1] = password.charAt(1) - '0';
                passwords[i][2] = password.charAt(2) - '0';
                passwords[i][3] = password.charAt(3) - '0';
                int distance = distance(start, passwords[i]);
                if(nextDistance > distance) {
                    nextDistance = distance;
                    nextIdx = i;
                }
            }
            int[] shortestDistances = new int[length];
            int totalDistance = 0;
            Arrays.fill(shortestDistances, Integer.MAX_VALUE);
            shortestDistances[nextIdx] = nextDistance; 
            HashSet<Integer> visited = new HashSet<>();
            while(true) {
                int curIdx = nextIdx;
                totalDistance += shortestDistances[curIdx];
                shortestDistances[curIdx] = 0;
                visited.add(curIdx);
                if(visited.size() == length) {
                    break;
                }
                nextDistance = Integer.MAX_VALUE;
                for(int i = 0; i < length; ++i) {
                    if(shortestDistances[i] > 0) {
                        int distance = distance(passwords[i], passwords[curIdx]);
                        if(shortestDistances[i] > distance) {
                            shortestDistances[i] = distance;
                        }
                        if(nextDistance > shortestDistances[i]) {
                            nextDistance = shortestDistances[i];
                            nextIdx = i;
                        }
                    }
                }
            }
            System.out.println(totalDistance);
        }
    }

    private static int distance(Integer[] nums1, Integer[] nums2) {
        int result = 0;
        for(int i = 0; i <4; ++i) {
            int dist = Math.abs(nums1[i] - nums2[i]);
            result += dist > 5? 10-dist : dist;
        }
        return result;
    }

}