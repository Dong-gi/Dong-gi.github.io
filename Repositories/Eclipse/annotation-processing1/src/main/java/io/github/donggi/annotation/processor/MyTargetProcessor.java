package io.github.donggi.annotation.processor;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

import io.github.donggi.annotation.MyTarget;

@SupportedSourceVersion(SourceVersion.RELEASE_12)
@SupportedAnnotationTypes("io.github.donggi.annotation.MyTarget")
public class MyTargetProcessor extends AbstractProcessor {

    private int round = 1;
    
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        var processed = false;
        final var suffix = "Generated";
        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, String.format("[ROUND %2d] It's my turn.", round++));
        for(var annotation : annotations) {
            for(var element : roundEnv.getElementsAnnotatedWith(annotation)) {
                var className = element.toString();
                var packageName = className.substring(0, className.lastIndexOf('.'));
                // printMessage의 3번째 요소를 넘기면 코드 정보가 출력된다
                processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "", element);
                processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, String.format("Annotation '%s' found at '%s'", annotation, className));
                processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, String.format("Annotation value : %s", element.getAnnotation(MyTarget.class).value()));
                try {
                    if(className.endsWith(suffix)) {
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, String.format("Skipping generated file '%s'", className));
                        processed = true;
                        continue;
                    }
                    var newClassName = className + suffix;
                    var newSimpleClassName = element.getSimpleName() + suffix;
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, String.format("Creating '%s' -> '%s'", className, newClassName));
                    Files.deleteIfExists(Path.of("./", newClassName.replace('.', '/')));
                    var file = processingEnv.getFiler().createSourceFile(newClassName);
                    try(var writer = new PrintWriter(file.openWriter())) {
                        writer.append("package ").append(packageName).println(";\n");
                        writer.println("import io.github.donggi.annotation.MyTarget;\n");
                        writer.println("@MyTarget");
                        writer.append("public class ").append(newSimpleClassName).println(" {");
                        writer.println("}");
                    }
                    processed = true;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return processed;
    }

}
