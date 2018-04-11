import java.io.FileReader;
import java.io.StreamTokenizer;

public class StreamTokenizerExample {

	public static void main(String[] args) throws Exception {
		/* Just a comments */
		// 1 + 1 = 2?
		var t = new StreamTokenizer(new FileReader("./src/StreamTokenizerExample.java"));
		int type;
		while(true) {
			type = t.nextToken();
			if(type == StreamTokenizer.TT_EOF) break;
			System.out.printf("type: %3d, line: %3d", type, t.lineno());
			switch(type) {
			case StreamTokenizer.TT_EOL:
				System.out.println(", line end");
				break;
			case StreamTokenizer.TT_NUMBER:
				System.out.println(", number :" + t.nval);
				break;
			case StreamTokenizer.TT_WORD:
				System.out.println(", string :" + t.sval);
				break;
			default:
				System.out.println(", special:" + (char)type);
			}
		}
	}

}
