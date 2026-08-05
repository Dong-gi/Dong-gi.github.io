package io.github.donggi.util;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.LinkedList;
import java.util.List;

import org.springframework.beans.propertyeditors.CustomDateEditor;

public class MultipleDateEditor extends CustomDateEditor {

    private List<CustomDateEditor> editors = new LinkedList<>();

    private MultipleDateEditor(DateFormat dateFormat, boolean allowEmpty) {
        super(dateFormat, allowEmpty);
    }

    public static MultipleDateEditor of(boolean allowEmpty, String... formats) {
        return of(allowEmpty, -1, formats);
    }

    public static MultipleDateEditor of(boolean allowEmpty, int exactDateLength, String... formats) {
        var o = new MultipleDateEditor(null, false);
        for(var format : formats)
            o.editors.add(new CustomDateEditor(new SimpleDateFormat(format), allowEmpty, exactDateLength));
        return o;
    }

    @Override
    public void setAsText(String text) throws IllegalArgumentException {
        var iter = editors.iterator();
        while(iter.hasNext()) {
            var editor = iter.next();
            try {
                editor.setAsText(text);
                setValue(editor.getValue());
                break;
            } catch (IllegalArgumentException e) {
                if(iter.hasNext())
                    continue;
                throw e;
            }
        }
    }

    @Override
    public String getAsText() {
        for(var editor : editors)
            return editor.getAsText();
        return null;
    }
}
