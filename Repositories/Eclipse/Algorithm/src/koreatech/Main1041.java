package koreatech;

import java.util.*;

public class Main1041 {

    private static ArrayList<Integer> xPositions = new ArrayList<>();
    private static ArrayList<Integer> yPositions = new ArrayList<>();
    private static ArrayList<Integer> xValues = new ArrayList<>();
    private static ArrayList<Integer> yValues = new ArrayList<>();

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        int size = in.nextInt();

        for(int i=0; i<size; ++i) {
            xPositions.add(in.nextInt());
            yPositions.add(in.nextInt());
        }
        HashSet<Integer> set = new HashSet<>();
        set.addAll(xPositions);
        xValues.addAll(set);
        set.clear();
        set.addAll(yPositions);
        yValues.addAll(set);

        xPositions.sort(Comparator.comparing(Integer::intValue));
        xValues.sort(Comparator.comparing(Integer::intValue));
        yPositions.sort(Comparator.comparing(Integer::intValue));
        yValues.sort(Comparator.comparing(Integer::intValue));

        int distance = 0;
        // check x
        {
            int checkPoint = xValues.get((xValues.size()-1)/2);
            int checkIdx = xValues.indexOf(checkPoint);
            boolean hasFound = false;
            int numOfLeftPoints, numOfRightPoints;
            while(!hasFound) {
                numOfLeftPoints = xPositions.indexOf(checkPoint);
                if(checkIdx == xValues.size()-1)
                    numOfRightPoints = 0;
                else
                    numOfRightPoints = size - xPositions.indexOf(xValues.get(checkIdx+1));

                if(numOfLeftPoints > size - numOfLeftPoints) {
                    checkPoint = xValues.get(--checkIdx);
                    continue;
                }
                if(size - numOfRightPoints < numOfRightPoints) {
                    checkPoint = xValues.get(++checkIdx);
                    continue;
                }
                hasFound = true;
            }
            //System.out.println(checkPoint);
            for(int item : xPositions)
                distance += Math.abs(item - checkPoint);
        }

        // check y
        {
            int checkPoint = yValues.get((yValues.size()-1)/2);
            int checkIdx = yValues.indexOf(checkPoint);
            boolean hasFound = false;
            int numOfLeftPoints, numOfRightPoints;
            while(!hasFound) {
                numOfLeftPoints = yPositions.indexOf(checkPoint);
                if(checkIdx == yValues.size()-1)
                    numOfRightPoints = 0;
                else
                    numOfRightPoints = size - yPositions.indexOf(yValues.get(checkIdx+1));

                if(numOfLeftPoints > size - numOfLeftPoints) {
                    checkPoint = yValues.get(--checkIdx);
                    continue;
                }
                if(size - numOfRightPoints < numOfRightPoints) {
                    checkPoint = yValues.get(++checkIdx);
                    continue;
                }
                hasFound = true;
            }
            //System.out.println(checkPoint);
            for(int item : yPositions)
                distance += Math.abs(item - checkPoint);
        }
        System.out.println(distance);
    }
    
}