import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Date;

import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.collections4.ListUtils;
import org.junit.Test;

import lombok.Builder;
import lombok.Data;

public class ListUtilsTest {

	@Builder
	@Data
	static class IUser {
		Integer userId;
		Date birthday;
	}
	
	@Test
	public void lazyListTest() {
		var birthdays = ListUtils.lazyList(new ArrayList<Date>(), () -> new Date());
		var user0 = IUser.builder().userId(0).birthday(birthdays.get(0)).build();
		var user4 = IUser.builder().userId(4).birthday(birthdays.get(4)).build();
		
		System.out.println(user0);
		System.out.println(user4);
		assertTrue(!user0.getBirthday().after(user4.getBirthday())); // OK
		assertTrue(birthdays.size() > 2); // OK
		System.out.println(IterableUtils.toString(birthdays));
		// [Wed Aug 07 18:05:55 KST 2019, null, null, null, Wed Aug 07 18:05:55 KST 2019]
		assertTrue(birthdays.get(2).after(user0.getBirthday()) && birthdays.get(2).before(user4.getBirthday())); // NG
	}

}
