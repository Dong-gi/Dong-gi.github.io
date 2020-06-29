package io.github.donggi.anno;

import java.text.ParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.format.AnnotationFormatterFactory;
import org.springframework.format.Formatter;
import org.springframework.format.Parser;
import org.springframework.format.Printer;

import lombok.AllArgsConstructor;

public class Test1FormatAnnotationFormatterFactory implements AnnotationFormatterFactory<Test1Format> {

    @Override
    public Set<Class<?>> getFieldTypes() {
        return new HashSet<Class<?>>(Arrays.asList(Integer.class));
    }

    @Override
    public Printer<Integer> getPrinter(Test1Format annotation, Class<?> fieldType) {
        return new Test1IntegerFormatter(annotation);
    }

    @Override
    public Parser<Integer> getParser(Test1Format annotation, Class<?> fieldType) {
        return new Test1IntegerFormatter(annotation);
    }

}

@AllArgsConstructor
class Test1IntegerFormatter implements Formatter<Integer> {

    private Test1Format annotation;

    @Override
    public String print(Integer object, Locale locale) {
        return String.format("%s%d%s", annotation.prefix(), object, annotation.suffix());
    }

    @Override
    public Integer parse(String text, Locale locale) throws ParseException {
        if (!text.startsWith(annotation.prefix()))
            throw new ParseException(String.format("prefix must be %s", annotation.prefix()), 0);
        if (!text.endsWith(annotation.suffix()))
            throw new ParseException(String.format("suffix must be %s", annotation.suffix()), text.length() - annotation.suffix().length());
        return Integer.valueOf(text.substring(annotation.prefix().length(), text.length() - annotation.suffix().length()));
    }

}