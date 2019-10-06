import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockMultipartHttpServletRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import io.github.donggi.controller.HelloController;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration({"file:src/main/webapp/WEB-INF/applicationContext.xml",
"file:src/main/webapp/WEB-INF/dispatcher-servlet.xml"})
public class Test {

    @Autowired
    private HelloController helloController;
    @Autowired
    private String commonMessage;

    @org.junit.Test
    public void beanTest() {
        System.out.println(commonMessage);
    }
    
    @org.junit.Test
    public void requestTest() {
        var request = new MockHttpServletRequest("GET", null);
        var mav = helloController.commonHello(request);
        System.out.println(mav.getViewName());
    }
    
    @org.junit.Test
    public void multipartRequestTest() throws IOException {
        var request = new MockMultipartHttpServletRequest();
        request.setMethod("POST");
        final var fileName = "src/main/webapp/WEB-INF/applicationContext.xml";
        request.addFile(new MockMultipartFile("files", fileName, null, Files.newInputStream(Path.of(fileName))));
    }    

}
