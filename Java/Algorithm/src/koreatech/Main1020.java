package koreatech;

import java.util.*;

public class Main1020 {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int testCase = scanner.nextInt();
        while(testCase-- > 0) {
            char[] id = scanner.next().toCharArray();
            int checkSum = 0;
            for(int i = 0; i <= 16; ++i) {
                checkSum += (id[i]-'0');
                checkSum *= 2;
            }
            if((checkSum%11 != (id[17] == 'X'? 10 : id[17]-'0'))
                    || ((id[0]+id[1]+id[2]+id[3]+id[4]+id[5]) != 6*'0'+1)
                    || !checkDate(id)
                    || ((id[14]+id[15]+id[16] == 3*'0'))) {
                System.out.println("Invalid");
            } else {
                System.out.println(((id[16]-'0')%2 == 1? "Male" : "Female"));
            }
        }
    }

    private static boolean checkDate(char[] id) {
        int year = Integer.parseInt(""+id[6]+id[7]+id[8]+id[9]);
        int month = Integer.parseInt(""+id[10]+id[11]);
        int day = Integer.parseInt(""+id[12]+id[13]);
        //System.out.printf("%d, %d, %d\n", year, month, day);
        if(year < 1900 || year > 2014) {
            return false;
        }
        switch(month) {
        case 1: case 3: case 5: case 7: case 8: case 10: case 12:
            return day > 0 && day < 32;
        case 4: case 6: case 9: case 11:
            return day > 0 && day < 31;
        case 2:
            if((year%400 == 0) || ((year%100 != 0) && (year%4 == 0)))
                return day > 0 && day < 30;
            return day > 0 && day < 29;
        default: return false;
        }
    }

}