package donggi.controller;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.io.IOException;

public class ListViewController extends BaseController {

    private TextField listViewExample_text;
    private ListView<String> listViewExample_list;

    public ListViewController(Stage stage) {
        super(stage);
    }

    public ListViewController() {
    } // FXML 로드하면서 Controller 생성시 필요

    @Override
    public String getDescription() {
        return "리스트뷰(ListView) 예제";
    }

    @Override
    public void exec() {
        var dialog = new Dialog();
        try {
            dialog.getDialogPane().setContent(FXMLLoader.load(getClass().getResource("../fxml/list_view_example.fxml")));
        } catch (IOException e) {
        }
        dialog.setTitle("리스트뷰");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL);
        dialog.show();

        listViewExample_list = (ListView<String>) dialog.getDialogPane().lookup("#listViewExample_list");
        listViewExample_text = (TextField) dialog.getDialogPane().lookup("#listViewExample_text");

        listViewExample_list.getItems().addAll("bold", "italic");
        listViewExample_list.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        listViewExample_list.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            final var style = new StringBuffer("-fx-font-family: Verdana; -fx-font-size: 18;");
            final var bold = "-fx-font-weight: bold;";
            final var italic = "-fx-font-style: italic;";
            listViewExample_list.getSelectionModel().getSelectedItems().forEach(item -> {
                switch (item) {
                    case "bold":
                        style.append(bold);
                        break;
                    case "italic":
                        style.append(italic);
                }
            });
            listViewExample_text.setStyle(style.toString());
        });
    }
}
