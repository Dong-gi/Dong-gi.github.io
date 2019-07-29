package donggi.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.io.IOException;

public class CheckBoxController extends BaseController {

    private TextField checkBoxExample_text;
    private CheckBox checkBoxExample_bold, checkBoxExample_italic;

    public CheckBoxController(Stage stage) {
        super(stage);
    }

    public CheckBoxController() {
    } // FXML 로드하면서 Controller 생성시 필요

    @Override
    public String getDescription() {
        return "체크박스(CheckBox) 예제";
    }

    @Override
    public void exec() {
        var dialog = new Dialog();
        try {
            dialog.getDialogPane().setContent(FXMLLoader.load(getClass().getResource("../fxml/check_box_example.fxml")));
        } catch (IOException e) {
        }
        dialog.setTitle("체크박스");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        dialog.show();

        checkBoxExample_bold = (CheckBox) dialog.getDialogPane().lookup("#checkBoxExample_bold");
        checkBoxExample_italic = (CheckBox) dialog.getDialogPane().lookup("#checkBoxExample_italic");
        checkBoxExample_text = (TextField) dialog.getDialogPane().lookup("#checkBoxExample_text");

        checkBoxExample_bold.selectedProperty().addListener((observable, oldValue, newValue) -> {
            checkBoxExample_text.setFont(Font.font("Verdana",
                    newValue ? FontWeight.BOLD : FontWeight.NORMAL,
                    checkBoxExample_italic.isSelected() ? FontPosture.ITALIC : FontPosture.REGULAR,
                    18));
        });
        checkBoxExample_italic.selectedProperty().addListener((observable, oldValue, newValue) -> {
            checkBoxExample_text.setFont(Font.font("Verdana",
                    checkBoxExample_bold.isSelected() ? FontWeight.BOLD : FontWeight.NORMAL,
                    newValue ? FontPosture.ITALIC : FontPosture.REGULAR,
                    18));
        });
    }
}
