import java.io.FileInputStream;
import java.io.FileOutputStream;

public class InputStreamExample {

	public static void main(String[] args) throws Exception {
		try(FileOutputStream out = new FileOutputStream("string.data");) {
			out.write("Hello World".getBytes());
		}

		try(FileInputStream in = new FileInputStream("string.data");
				FileOutputStream out = new FileOutputStream("string2.data");) {
			System.out.println(in.available());
			in.transferTo(out);
		}

		try(FileInputStream in = new FileInputStream("string2.data");) {
			System.out.println(new String(in.readAllBytes()));
		}
	}

}
