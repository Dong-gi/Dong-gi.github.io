package koreatech;

import java.util.*;

public class Main1034 {

    private static ArrayList<Integer> positions = new ArrayList<>();
    private static ArrayList<Integer> values = new ArrayList<>();

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        int size = in.nextInt();

        while(size-- > 0)
            positions.add(in.nextInt());
        HashSet<Integer> set = new HashSet<>();
        set.addAll(positions);
        values.addAll(set);

        size = positions.size();
        int checkPoint = values.get((values.size()-1)/2);
        int checkIdx = values.indexOf(checkPoint);
        boolean hasFound = false;
        int numOfLeftPoints, numOfRightPoints;
        while(!hasFound) {
            numOfLeftPoints = positions.indexOf(checkPoint);
            if(checkIdx == values.size()-1)
                numOfRightPoints = 0;
            else
                numOfRightPoints = size - positions.indexOf(values.get(checkIdx+1));

            if(numOfLeftPoints > size - numOfLeftPoints) {
                checkPoint = values.get(--checkIdx);
                continue;
            }
            if(size - numOfRightPoints < numOfRightPoints) {
                checkPoint = values.get(++checkIdx);
                continue;
            }
            hasFound = true;
        }
        //System.out.println(checkPoint);

        int distance = 0;
        for(int item : positions)
            distance += Math.abs(item - checkPoint);
        System.out.println(distance);
    }

}