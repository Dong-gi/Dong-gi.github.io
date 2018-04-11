import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Scanner;

public class PipedStreamExample {
	private static PipedInputStream in;
	private static PipedOutputStream out;
	
	public static void main(String[] args) throws Exception {
		in = new PipedInputStream();
		out = new PipedOutputStream(in);
		
		new Thread(() -> {
			while(true) {
				try {
					if(in.available() > 0) {
						var buf = new byte[in.available()];
						in.read(buf);
						System.out.println("Received: " + new String(buf));
					}
					Thread.sleep(100);
				} catch (Exception e) {}
			}
		}).start();
		
		var scanner = new Scanner(System.in);
		while(true) {
			System.out.print("Write Something: ");
			out.write(scanner.nextLine().getBytes());
			out.flush();
			Thread.sleep(200);
		}
	}

}
