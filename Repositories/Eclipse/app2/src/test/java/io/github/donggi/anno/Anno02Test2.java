package io.github.donggi.anno;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.FileSystemResource;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Anno02Test2 {
    @Test
    public void test() throws IOException {
        try (var context = new AnnotationConfigApplicationContext()) {
            // Add *.properties files
            PropertySourcesPlaceholderConfigurer pConfig = new PropertySourcesPlaceholderConfigurer();
            pConfig.setFileEncoding("UTF-8");
            pConfig.setIgnoreResourceNotFound(true);
            pConfig.setLocations(Files.list(Paths.get("src/main/resource/"))
                    .filter(x -> x.toFile().getName().endsWith(".properties"))
                    .map(x -> new FileSystemResource(x.toFile()))
                    .toArray(FileSystemResource[]::new));

            // Set context config
            context.scan("io.github.donggi.anno");
            context.addBeanFactoryPostProcessor(pConfig);
            context.refresh();

            // Using it
            for (var name : context.getBeanNamesForType(Anno02.class)) {
                var anno02 = (Anno02)context.getBean(name);
                log.infof("{} bean's arr = {}", name, Arrays.toString(anno02.getAnno02Arr()));
            }
        }
    }
/*
16:58:35.709 [main] INFO io.github.donggi.anno.Anno02Test2 - anno02 bean's arr = [Anno02C1(message=Default Message By Annotation@64212395068700), Anno02C2(message=Default Message By Annotation@64212395987800)]
16:58:35.709 [main] INFO io.github.donggi.anno.Anno02Test2 - anno02C1 bean's arr = [Anno02(message=Default Message By Annotation@64212390014300), Anno02C2(message=Default Message By Annotation@64212395987800)]
16:58:35.709 [main] INFO io.github.donggi.anno.Anno02Test2 - anno02C2 bean's arr = [Anno02(message=Default Message By Annotation@64212390014300), Anno02C1(message=Default Message By Annotation@64212395068700)]
*/
}
