package donggi.controller;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.io.IOException;

public class RadioButtonController extends BaseController {

    private TextField radioButtonExample_text;
    private RadioButton radioButtonExample_bold, radioButtonExample_italic;

    public RadioButtonController(Stage stage) {
        super(stage);
    }

    public RadioButtonController() {
    } // FXML 로드하면서 Controller 생성시 필요

    @Override
    public String getDescription() {
        return "라디오버튼(RadioButton) 예제";
    }

    @Override
    public void exec() {
        var dialog = new Dialog();
        try {
            dialog.getDialogPane().setContent(FXMLLoader.load(getClass().getResource("../fxml/radio_button_example.fxml")));
        } catch (IOException e) {
        }
        dialog.setTitle("라디오 버튼");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL);
        dialog.show();

        radioButtonExample_bold = (RadioButton) dialog.getDialogPane().lookup("#radioButtonExample_bold");
        radioButtonExample_italic = (RadioButton) dialog.getDialogPane().lookup("#radioButtonExample_italic");
        radioButtonExample_text = (TextField) dialog.getDialogPane().lookup("#radioButtonExample_text");

        radioButtonExample_bold.selectedProperty().addListener((observable, oldValue, newValue) -> {
            radioButtonExample_text.setFont(Font.font("Verdana",
                    newValue ? FontWeight.BOLD : FontWeight.NORMAL,
                    radioButtonExample_italic.isSelected() ? FontPosture.ITALIC : FontPosture.REGULAR,
                    18));
        });
        radioButtonExample_italic.selectedProperty().addListener((observable, oldValue, newValue) -> {
            radioButtonExample_text.setFont(Font.font("Verdana",
                    radioButtonExample_bold.isSelected() ? FontWeight.BOLD : FontWeight.NORMAL,
                    newValue ? FontPosture.ITALIC : FontPosture.REGULAR,
                    18));
        });
    }
}
