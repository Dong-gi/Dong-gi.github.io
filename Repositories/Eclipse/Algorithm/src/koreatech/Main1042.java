package koreatech;

import java.util.*;

public class Main1042 {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int size = scanner.nextInt();
        ArrayList<Integer> list = new ArrayList<>();
        while(size-- > 0)
            list.add(scanner.nextInt());
        
        // 피크만 남김
        for(int i = 0; i + 2 < list.size();) {
            if(list.get(i) == list.get(i+1))
                list.remove(i);
            else if(list.get(i) <= list.get(i+1) && list.get(i+1) <= list.get(i+2))
                list.remove(i+1);
            else if(list.get(i) >= list.get(i+1) && list.get(i+1) >= list.get(i+2))
                list.remove(i+1);
            else
                ++i;
        }
        // 앞뒤의 쓸모없는 데이터 제거
        if(list.size() > 1 && list.get(0) >= list.get(1)) list.remove(0);
        if(list.size() > 1 && list.get(list.size()-1) <= list.get(list.size()-2)) list.remove(list.size()-1);

        int max = 0;
        if(list.size() > 1) {
            for(int i = 0; i < list.size(); i+=2) {
                int benefit = benefit(list, 0, i) + benefit(list, i, list.size());
                max = (max > benefit)? max : benefit;
            }
        }

        System.out.println(max);
    }

    private static int benefit(ArrayList<Integer> list, int from, int to) {
        if(from+2 > to) return 0;

        int[] prices = new int[2];
        prices[0] = list.get(from);
        prices[1] = list.get(from+1);
        int max = prices[1] - prices[0];
        try {
            for(int i = from+2; i < to; i += 2) {
                // 하한가 떨어지면 무조건 삼
                if(list.get(i) < prices[0]) {
                    prices[0] = list.get(i);
                    prices[1] = 0;
                }
                // 상한가 오르면 무조건 팖 
                if(list.get(i+1) > prices[1]) {
                    prices[1] = list.get(i+1);
                    max = (max > prices[1] - prices[0])? max : prices[1] - prices[0];
                }
            }
        } catch(Exception e) {}
        return max;
    }
}