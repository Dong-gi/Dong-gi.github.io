import java.io.PipedReader;
import java.io.PipedWriter;
import java.util.Scanner;

public class PipedReaderExample {
	private static PipedReader in;
	private static PipedWriter out;
	
	public static void main(String[] args) throws Exception {
		in = new PipedReader();
		out = new PipedWriter(in);
		
		new Thread(() -> {
			var buf = new char[1000];
			while(true) {
				try {
					if(in.ready()) {
						in.read(buf);
						System.out.println("Received: " + new String(buf).trim());
					}
					Thread.sleep(100);
				} catch (Exception e) {}
			}
		}).start();
		
		var scanner = new Scanner(System.in);
		while(true) {
			System.out.print("Write Something: ");
			var data = scanner.nextLine().toCharArray();
			out.write(data, 0, data.length);
			out.flush();
			Thread.sleep(200);
		}
	}

}
