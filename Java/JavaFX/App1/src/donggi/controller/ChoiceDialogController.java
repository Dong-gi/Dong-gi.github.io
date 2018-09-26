package donggi.controller;

import javafx.scene.control.Alert;
import javafx.scene.control.ChoiceDialog;
import javafx.stage.Stage;

public class ChoiceDialogController extends BaseController {

    public ChoiceDialogController(Stage stage) {
        super(stage);
    }

    @Override
    public String getDescription() {
        return "선택 대화창(ChoiceDialog) 예제";
    }

    @Override
    public void exec() {
        var dialog = new ChoiceDialog<>("", new String[]{"부먹", "찍먹"});
        dialog.setTitle("고르시오");
        dialog.setHeaderText("");
        dialog.setContentText("당신의 취향은? : ");
        print(dialog.showAndWait().toString());
    }
}
