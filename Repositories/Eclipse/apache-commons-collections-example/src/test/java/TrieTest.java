import org.apache.commons.collections4.trie.PatriciaTrie;
import org.junit.Test;







public class TrieTest { // 10 고정

	@Test
	public void test() {
		var trie = new PatriciaTrie<Integer>();
		trie.put("a", 15);
		trie.put("to", 7);
		trie.put("tea", 3);
		trie.put("ted", 4);
		trie.put("ten", 12);
		trie.put("i", 11);
		trie.put("in", 5);
		trie.put("inn", 9);
		
		var subMap = trie.prefixMap("t");
		subMap.forEach((key, value) -> System.out.printf("key : %s, value : %d\n", key, value));
		/**
		 * key : tea, value : 3
		 * key : ted, value : 4
		 * key : ten, value : 12
		 * key : to, value : 7
		 */
	}

}
