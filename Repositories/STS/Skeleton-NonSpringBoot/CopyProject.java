import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

public class CopyProject {
    public static final String OLD_PROJECT_NAME = "Skeleton-NonSpringBoot";

    public static void main(String[] args) throws IOException {
        if (args.length < 1)
            System.out.println("java CopyProject.java \"NewProjectName\"");
        else
            copyProject(args[0]);
    }

    public static void copyProject(String projectName) throws IOException {
        var targetDir = Files.createDirectory(Path.of("../", projectName));
        copy(Path.of("./", "gradle"), targetDir);
        copy(Path.of("./", "src"), targetDir);
        copy(Path.of("./", ".classpath"), targetDir);
        Files.writeString(targetDir.resolve(".project"),
            Files.readString(Path.of("./", ".project")).replace(OLD_PROJECT_NAME, projectName));
        copy(Path.of("./", "build.gradle"), targetDir);
        copy(Path.of("./", "gradlew"), targetDir);
        copy(Path.of("./", "gradlew.bat"), targetDir);
        Files.writeString(targetDir.resolve("settings.gradle"),
            Files.readString(Path.of("./", "settings.gradle")).replace(OLD_PROJECT_NAME, projectName));
    }

    public static void copy(Path from, Path to) throws IOException {
        if (Files.exists(to) == false)
            Files.createDirectory(to);

        if (Files.isDirectory(from) == false) {
            Files.copy(from, to.resolve(from.getFileName()));
            return;
        }

        to = to.resolve(from.getFileName());
        if (Files.exists(to) == false)
            Files.createDirectory(to);

        for (var child : Files.list(from).collect(Collectors.toList())) {
            if (Files.isDirectory(child))
                copy(child, to);
            else
                Files.copy(child, to.resolve(from.relativize(child)));
        }
    }
}
