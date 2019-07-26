package donggi.controller;

import javafx.scene.control.Alert;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Stage;

public class TextInputDialogController extends BaseController {

    public TextInputDialogController(Stage stage) {
        super(stage);
    }

    @Override
    public String getDescription() {
        return "텍스트 입력 대화창(TextInputDialog) 예제";
    }

    @Override
    public void exec() {
        var dialog = new TextInputDialog();
        dialog.setTitle("텍스트 입력");
        dialog.setHeaderText("");
        dialog.setContentText("ID : ");
        print(dialog.showAndWait().toString());
    }
}
