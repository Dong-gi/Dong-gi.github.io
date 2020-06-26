package io.github.donggi.anno;

import java.text.ParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.format.AnnotationFormatterFactory;
import org.springframework.format.Parser;
import org.springframework.format.Printer;

public class MyFormatAnnotationFormatterFactory implements AnnotationFormatterFactory<MyFormat> {

    @Override
    public Set<Class<?>> getFieldTypes() {
        return new HashSet<Class<?>>(Arrays.asList(String.class));
    }

    @Override
    public Printer<String> getPrinter(MyFormat annotation, Class<?> fieldType) {
        return new Printer<>() {
            @Override
            public String print(String object, Locale locale) {
                return String.format("%s%s%s", annotation.prefix(), object, annotation.suffix());
            }
        };
    }

    @Override
    public Parser<String> getParser(MyFormat annotation, Class<?> fieldType) {
        return new Parser<>() {
            @Override
            public String parse(String text, Locale locale) throws ParseException {
                return text.substring(annotation.prefix().length(), text.length() - annotation.suffix().length());
            }
        };
    }

}
