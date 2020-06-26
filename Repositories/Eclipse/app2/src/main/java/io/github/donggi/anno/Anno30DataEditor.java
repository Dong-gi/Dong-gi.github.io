package io.github.donggi.anno;

import java.beans.PropertyEditorSupport;

public class Anno30DataEditor extends PropertyEditorSupport {
    @Override
    public void setAsText(String text) throws IllegalArgumentException {
        var d = new Anno30Data();
        var splits = text.split("@");
        d.setName(splits[0]);
        d.setDomain(splits[1]);
        setValue(d);
    }
}