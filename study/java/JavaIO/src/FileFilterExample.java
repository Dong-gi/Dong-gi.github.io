import java.io.File;

public class FileFilterExample {

	public static void main(String[] args) {
		File root = new File("C:\\");
		
		System.out.println("전체 목록");
		for(File file : root.listFiles()) {
			System.out.print(file.getName() + ", ");
		}
		
		System.out.println("\n\n공백이 있는 목록");
		for(File file : root.listFiles((file) -> file.getName().contains(" "))) {
			System.out.print(file.getName() + ", ");
		}
	}

}
