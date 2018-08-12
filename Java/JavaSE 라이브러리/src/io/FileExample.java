package io;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;

public class FileExample {

	public static void main(String[] args) throws IOException {
		System.out.println(Arrays.toString(
				Arrays.stream(File.listRoots()).map(file -> {
					try {
						return file.getCanonicalPath();
					} catch (IOException e) {}
					return "";
				}).toArray()));
		
		System.out.println(System.getProperty("user.dir"));
		var file = new File("./");
		System.out.println(Arrays.toString(file.list()));
		
		try(var writer = new FileWriter("string3.data")) {
			writer.append("Hello World!").close();
		}
		System.out.println(Arrays.toString(file.list()));

		file = new File("./string3.data");
		System.out.println("Exist     : " + file.exists());
		System.out.println("Delete    : " + file.delete());
		System.out.println("Exist     : " + file.exists());
		System.out.println("Create    : " + file.createNewFile());
		System.out.println("Exist     : " + file.exists());
		file.deleteOnExit();

		System.out.println("Is File   : " + file.isFile());
		System.out.println("Is Dir    : " + file.isDirectory());
		System.out.println("Is Hidden : " + file.isHidden());
		System.out.println("Last Modified : " + new Date(file.lastModified()));

		System.out.println("Can Read  : " + file.canRead());
		System.out.println("Can Write : " + file.canWrite());
		System.out.println("Can Exec  : " + file.canExecute());

		System.out.println("Path           : " + file.getPath());
		System.out.println("Absolute Path  : " + file.getAbsolutePath());
		System.out.println("Canonical Path : " + file.getCanonicalPath());
		System.out.println("URI            : " + file.toURI().toString());
	}

}