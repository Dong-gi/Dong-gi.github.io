package koreatech;

import java.util.*;

public class Main1059 {

    public static void main(String[] args) {
        final Scanner in = new Scanner(System.in);
        final Integer[] nums = new Integer[in.nextInt()];
        for(int i = 0; i < nums.length; ++i)
            nums[i] = in.nextInt();

        final ArrayList<Integer> list = new ArrayList<>();
        Collections.addAll(list, nums);

        // 피크만 남기기
        for(int i = 0; i + 2 < list.size();) {
            // a=b : 하나 제거
            if(list.get(i) == list.get(i+1)) {
                list.remove(i);
                continue;
            }
            // b=c : (a, b) 완료
            if(list.get(i+1) == list.get(i+2)) {
                i += 2;
                continue;
            }
            // 3개 이하로 남은 경우 종료
            if(i + 3 >= list.size())
                break;
            // 4개 연속으로 증가하는 경우, 2번째 것 빼기
            if(list.get(i) < list.get(i+1) && list.get(i+1) < list.get(i+2) && list.get(i+2) <= list.get(i+3)) {
                list.remove(i+1);
                continue;
            }
            // 4개 연속으로 감소하는 경우, 2번째 것 빼기
            if(list.get(i) > list.get(i+1) && list.get(i+1) > list.get(i+2) && list.get(i+2) >= list.get(i+3)) {
                list.remove(i+1);
                continue;
            }
            // a < b > c < d 또는 a > b < c > d인 경우 : (a, b) 완료
            if(list.get(i) < list.get(i+1) && list.get(i+2) <= list.get(i+3)) {
                i += 2;
                continue;
            }
            if(list.get(i) > list.get(i+1) && list.get(i+2) >= list.get(i+3)) {
                i += 2;
                continue;
            }
            // a < b > c > d인 경우 : a < c면 (a, b) 분리, 아니면 a 삭제
            if(list.get(i) < list.get(i+1) && list.get(i+1) > list.get(i+2) && list.get(i+2) >= list.get(i+3)) {
                if(list.get(i) < list.get(i+2))
                    i += 2;
                else
                    list.remove(i);
                continue;
            }
            // a > b < c < d인 경우 : a > c면 (a, b) 분리, 아니면 a 삭제
            if(list.get(i) > list.get(i+1) && list.get(i+1) < list.get(i+2) && list.get(i+2) <= list.get(i+3)) {
                if(list.get(i) > list.get(i+2))
                    i += 2;
                else
                    list.remove(i);
                continue;
            }
            // a < b < c > d인 경우 : b > d면 (a, b) 분리, 아니면 b 삭제
            if(list.get(i) < list.get(i+1) && list.get(i+1) < list.get(i+2) && list.get(i+2) >= list.get(i+3)) {
                if(list.get(i+1) > list.get(i+3))
                    i += 2;
                else
                    list.remove(i+1);
                continue;
            }
            // a > b > c < d인 경우 : b < d면 (a, b) 분리, 아니면 b 삭제
            if(list.get(i) > list.get(i+1) && list.get(i+1) > list.get(i+2) && list.get(i+2) <= list.get(i+3)) {
                if(list.get(i+1) < list.get(i+3))
                    i += 2;
                else
                    list.remove(i+1);
                continue;
            }
        }

        int sum = 0;
        while(!list.isEmpty()) {
            if(list.size() > 3)
                sum += Math.abs(list.remove(0) - list.remove(0));
            else {
                final Integer[] rest = list.toArray(new Integer[0]);
                list.clear();
                Arrays.parallelSort(rest);
                sum += Math.abs(rest[0] - rest[rest.length-1]);
            }
        }
        System.out.print(sum);
    }
}
