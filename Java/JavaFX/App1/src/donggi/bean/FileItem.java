package donggi.bean;

import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.io.File;
import java.io.IOException;

public class FileItem {
    private StringProperty name;

    public void setName(String value) {
        nameProperty().set(value);
    }

    public String getName() {
        return nameProperty().get();
    }

    public StringProperty nameProperty() {
        if (name == null) name = new SimpleStringProperty(this, "name");
        return name;
    }

    private SimpleLongProperty lastModified;

    public void setLastModified(long value) {
        lastModifiedProperty().set(value);
    }

    public long getLastModified() {
        return lastModifiedProperty().get();
    }

    public SimpleLongProperty lastModifiedProperty() {
        if (lastModified == null) lastModified = new SimpleLongProperty(this, "lastModified");
        return lastModified;
    }

    private File file;

    public File getFile() {
        return file;
    }

    public FileItem(File file) {
        this.file = file;
        try {
            setName(file.getName().length() > 0 ? file.getName() : file.getCanonicalPath());
        } catch (IOException e) {
            setName(file.getAbsolutePath());
        }
        setLastModified(file.lastModified());
    }

    @Override
    public String toString() {
        return getName();
    }
}
