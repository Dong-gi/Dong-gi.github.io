import java.io.File;
import java.util.LinkedList;
import java.util.regex.Pattern;

public class Rename {
    public static void main(String[] args) {
        if (args.length < 2)
            System.out.println("java Rename.java \"(.+).jade\" \"\\$1.pug\"");
        else
            traverse(args[0], args[1]);
    }

    public static void traverse(String from, String to) {
        var pattern = Pattern.compile(from);
        var dirs = new LinkedList<File>();
        dirs.add(new File("./"));

        while (!dirs.isEmpty()) {
            var dir = dirs.remove(0);
            for (var file : dir.listFiles()) {
                if (file.isDirectory()) {
                    dirs.add(file);
                    continue;
                }
                var matcher = pattern.matcher(file.getName());
                if (matcher.matches()) {
                    var toFile = new File(file.getParentFile(), matcher.replaceAll(to));
                    if (!file.renameTo(toFile)) {
                        System.out.printf("Rename fail from [%s] to [%s]\n", file, toFile);
                    } else {
                        System.out.printf("Rename success from [%s] to [%s]\n", file, toFile);
                    }
                }
            }
        }
    }
}