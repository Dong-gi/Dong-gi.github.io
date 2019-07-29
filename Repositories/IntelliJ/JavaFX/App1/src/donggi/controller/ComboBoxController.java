package donggi.controller;

import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.io.IOException;

public class ComboBoxController extends BaseController {

    private TextField comboBoxExample_text;
    private ComboBox<String> comboBoxExample_combo;

    public ComboBoxController(Stage stage) {
        super(stage);
    }

    public ComboBoxController() {
    } // FXML 로드하면서 Controller 생성시 필요

    @Override
    public String getDescription() {
        return "콤보박스(ComboBox) 예제";
    }

    @Override
    public void exec() {
        var dialog = new Dialog();
        try {
            dialog.getDialogPane().setContent(FXMLLoader.load(getClass().getResource("../fxml/combo_box_example.fxml")));
        } catch (IOException e) {
        }
        dialog.setTitle("콤보박스");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL);
        dialog.show();

        comboBoxExample_combo = (ComboBox<String>) dialog.getDialogPane().lookup("#comboBoxExample_combo");
        comboBoxExample_text = (TextField) dialog.getDialogPane().lookup("#comboBoxExample_text");

        comboBoxExample_combo.getItems().addAll("bold", "italic");
        comboBoxExample_combo.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            switch (newValue) {
                case "bold":
                    comboBoxExample_text.setFont(Font.font("Verdana", FontWeight.BOLD, 18));
                    break;
                case "italic":
                    comboBoxExample_text.setFont(Font.font("Verdana", FontPosture.ITALIC, 18));
            }
        });
    }
}
