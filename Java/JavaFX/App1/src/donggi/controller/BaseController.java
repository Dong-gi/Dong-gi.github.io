package donggi.controller;

import javafx.scene.control.TextArea;
import javafx.stage.Stage;

public abstract class BaseController {
    private Stage stage;
    private TextArea out;

    protected BaseController() {
    }

    /**
     * @param stage 어느 스테이지에서 실행되는지를 명시
     */
    public BaseController(Stage stage) {
        this.stage = stage;
        out = (TextArea) stage.getScene().lookup("#out");
    }

    public abstract String getDescription();

    public abstract void exec();

    protected Stage getStage() {
        return stage;
    }

    public final void print(String str) {
        out.textProperty().setValue(str);
    }
}
