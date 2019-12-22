package nio;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class PathTest {

    void normalizeTest() {
        var path = Path.of("./", "hello", "..", "world");
        System.out.println(path);               // .\hello\..\world
        System.out.println(path.normalize());   // world
    }

    void resolveTest() {
        var basePath = Path.of("hello");
        System.out.println(basePath.resolve(Path.of("../world"))); // hello\..\world
    }

    void relativizeTest() {
        var basePath = Path.of("hello");
        System.out.println(basePath.relativize(Path.of("world")));          // ..\world
        System.out.println(basePath.relativize(Path.of("hello/../world"))); // ..\world
    }

    void toUriTest() {
        System.out.println(Path.of("hello").toUri());
        // file:///C:/tomcat/webapps/github/Repositories/Eclipse/JavaSE/hello
    }

    @Test
    void toAbsolutePathTest() {
        System.out.println(Path.of("hello").toAbsolutePath());
        // C:\tomcat\webapps\github\Repositories\Eclipse\JavaSE\hello
    }
}
