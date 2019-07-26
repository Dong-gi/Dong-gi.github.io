package donggi.controller;

import javafx.fxml.FXMLLoader;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.io.IOException;

public class CustomDialogController extends BaseController {

    public CustomDialogController(Stage stage) {
        super(stage);
    }

    @Override
    public String getDescription() {
        return "사용자 정의 대화창(Dialog) 예제";
    }

    @Override
    public void exec() {
        var dialog = new Dialog();
        try {
            dialog.getDialogPane().setContent(FXMLLoader.load(getClass().getResource("../fxml/main_scene.fxml")));
        } catch (IOException e) {
        }
        dialog.setTitle("사용자 정의 대화창");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK);
        ((TextArea)dialog.getDialogPane().lookup("#out")).setText("무언가 입력해보세요");
        dialog.setResultConverter(button -> button.toString() + "\n" + ((TextArea)dialog.getDialogPane().lookup("#out")).getText());
        dialog.showAndWait().ifPresent(str -> print(str.toString()));
    }
}
