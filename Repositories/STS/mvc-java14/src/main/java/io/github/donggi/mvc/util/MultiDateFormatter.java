package io.github.donggi.mvc.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.springframework.format.Formatter;

public class MultiDateFormatter implements Formatter<Date> {

    private List<CustomDateEditor> editors = new LinkedList<>();


    private MultiDateFormatter() {}

    public static MultiDateFormatter of(boolean allowEmpty, String... formats) {
        return of(allowEmpty, -1, formats);
    }

    public static MultiDateFormatter of(boolean allowEmpty, int exactDateLength, String... formats) {
        var o = new MultiDateFormatter();
        for(var format : formats)
            o.editors.add(new CustomDateEditor(new SimpleDateFormat(format), allowEmpty, exactDateLength));
        return o;
    }

    @Override
    public String print(Date object, Locale locale) {
        for(var editor : editors) {
            var text = editor.getAsText();
            if (text != null)
                return text;
        }
        return null;
    }

    @Override
    public Date parse(String text, Locale locale) throws ParseException {
        var iter = editors.iterator();
        while(iter.hasNext()) {
            var editor = iter.next();
            try {
                editor.setAsText(text);
                return (Date) editor.getValue();
            } catch (IllegalArgumentException e) {
                if(iter.hasNext())
                    continue;
                throw e;
            }
        }
        throw new ParseException("Can't parse [" + text + "] by " + editors.stream().map(x -> x.toString()).collect(Collectors.joining(",")), -1);
    }
}
