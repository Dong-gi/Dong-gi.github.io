import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

class StringComparator implements Comparator<String>{
	@Override
	public int compare(String o1, String o2) {
		return o1.compareTo(o2);
	}
}

public class SortTest {
	public static void batterSortTest(){
		List<Batter> list = new ArrayList<>();
		list.add(new Batter("김현수",50,0.326));
		list.add(new Batter("양의지",25,0.326));
		list.add(new Batter("허경민",13,0.317));
		list.add(new Batter("민병헌",49,0.303));
		
		Collections.sort(list);
		System.out.println(list);
		//list.sort((s1,s2)->(int)(s1.getBatAverage()*1000-s2.getBatAverage()*1000));
		list.sort(Comparator.comparingDouble(Batter::getBatAverage));
		System.out.println(list);
		list.sort(Comparator.comparingInt(Batter::getNumber));
		System.out.println(list);
		list.sort(Comparator.comparingInt(Batter::getNumber).reversed());
		System.out.println(list);
	}
	public static void main(String[] args) {
		List<String> list = Arrays.asList("apple", "grape", "kiwi", "banana", "rasberry");
		Collections.sort(list);
		//list.sort(new StringComparator());
		//list.sort((s1,s2)->s1.compareTo(s2));
		//list.sort((s1,s2)->s2.compareTo(s1));
		//list.sort(String::compareTo);
		//list.sort(Comparator.naturalOrder());
		//list.sort(Comparator.reverseOrder());
		
		//list.sort((s1,s2)->s1.length()-s2.length());
		//list.sort(Comparator.comparing(String::length));
		//list.sort(Comparator.comparing(String::length).reversed());
		
		//list.forEach(System.out::println);
		batterSortTest();
	}

}
