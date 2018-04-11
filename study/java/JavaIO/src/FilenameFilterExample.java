import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;

public class FilenameFilterExample { 

	public static void main(String[] args) {
		var dialog = new FileDialog(new Frame(), "Open txt");
		dialog.setFilenameFilter((dir, name)->{return name.contains(" ");});
		dialog.setVisible(true);
		System.out.println(dialog.getDirectory());
		System.out.println(dialog.getFile());
		System.out.println(dialog.getFilenameFilter().accept(new File(dialog.getDirectory()), dialog.getFile()));
		
		var root = new File("C:\\");
		System.out.println("\n전체 목록");
		for(var file : root.listFiles()) {
			System.out.print(file.getName() + ", ");
		}
		System.out.println("\n\n공백이 있는 목록");
		for(var file : root.listFiles((dir, name) -> name.contains(" "))) {
			System.out.print(file.getName() + ", ");
		}
	}

}