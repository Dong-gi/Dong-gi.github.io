package sample;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;

public class Controller {


    @FXML
    public void initialize() {

    }

    @FXML
    private void alertExample(ActionEvent e) throws Exception {
        var alert = new Alert(Alert.AlertType.INFORMATION);
        alert.titleProperty().bind(Main.primaryStage.titleProperty());
        alert.setHeaderText("이것이 알림창이다");
        alert.setContentText("부모 윈도우를 클릭해보세요");
        alert.show();
    }

    @FXML
    private void textInputDialogExample(ActionEvent e) throws Exception {
        var dialog = new TextInputDialog();
        dialog.setTitle("Input Text");
        dialog.setHeaderText("");
        dialog.setContentText("ID : ");
        System.out.println(dialog.showAndWait());
    }

    @FXML
    private void choiceDialogExample(ActionEvent e) throws Exception {
        var dialog = new ChoiceDialog<>("M", new String[]{"M", "F"});
        dialog.setTitle("Select Sex");
        dialog.setHeaderText("");
        dialog.setContentText("Sex : ");
        System.out.println(dialog.showAndWait());
    }

    @FXML
    private void customDialogExample(ActionEvent e) throws Exception {
        var dialog = new Dialog();
        dialog.getDialogPane().setContent(FXMLLoader.load(getClass().getResource("xml/sample.fxml")));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.show();
    }

    @FXML
    private void imageButtonExample(ActionEvent e) throws Exception {
        var dialog = new TextInputDialog();
        dialog.setTitle("Input URL");
        dialog.setHeaderText("");
        dialog.setContentText("Image URL : ");
        var image = new ImageView(new Image(dialog.showAndWait().orElse("http://via.placeholder.com/50x50")));
        image.setPreserveRatio(true);
        image.setFitWidth(50.);
        ((Button)e.getSource()).setGraphic(image);
    }

    private TextField checkBoxExample_text;
    private CheckBox checkBoxExample_bold, checkBoxExample_italic;
    @FXML
    private void checkBoxExample(ActionEvent e) throws Exception {
        var dialog = new Dialog();
        dialog.setTitle("CheckBox");
        Parent parent = FXMLLoader.load(getClass().getResource("xml/check_box_example.fxml"));
        dialog.getDialogPane().setContent(parent);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.show();

        parent.getChildrenUnmodifiable().forEach(node -> {
            switch(node.getId()) {
                case "checkBoxExample_bold":
                    checkBoxExample_bold = (CheckBox) node;
                    break;
                case "checkBoxExample_italic":
                    checkBoxExample_italic = (CheckBox) node;
                    break;
                case "checkBoxExample_text":
                    checkBoxExample_text = (TextField) node;
            }
        });

        checkBoxExample_bold.selectedProperty().addListener((observable, oldValue, newValue) -> {
            checkBoxExample_text.setFont(Font.font("Verdana",
                    newValue? FontWeight.BOLD : FontWeight.NORMAL,
                    checkBoxExample_italic.isSelected()? FontPosture.ITALIC : FontPosture.REGULAR,
                    18));
        });
        checkBoxExample_italic.selectedProperty().addListener((observable, oldValue, newValue) -> {
            checkBoxExample_text.setFont(Font.font("Verdana",
                    checkBoxExample_bold.isSelected()? FontWeight.BOLD : FontWeight.NORMAL,
                    newValue? FontPosture.ITALIC : FontPosture.REGULAR,
                    18));
        });
    }

    private TextField radioButtonExample_text;
    private RadioButton radioButtonExample_bold, radioButtonExample_italic;
    @FXML
    private void radioButtonExample(ActionEvent e) throws Exception {
        var dialog = new Dialog();
        dialog.setTitle("RadioButton");
        Parent parent = FXMLLoader.load(getClass().getResource("xml/radio_button_example.fxml"));
        dialog.getDialogPane().setContent(parent);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.show();

        parent.getChildrenUnmodifiable().forEach(node -> {
            switch(node.getId()) {
                case "radioButtonExample_bold":
                    radioButtonExample_bold = (RadioButton) node;
                    break;
                case "radioButtonExample_italic":
                    radioButtonExample_italic = (RadioButton) node;
                    break;
                case "radioButtonExample_text":
                    radioButtonExample_text = (TextField) node;
            }
        });

        radioButtonExample_bold.selectedProperty().addListener((observable, oldValue, newValue) -> {
            radioButtonExample_text.setFont(Font.font("Verdana",
                    newValue? FontWeight.BOLD : FontWeight.NORMAL,
                    radioButtonExample_italic.isSelected()? FontPosture.ITALIC : FontPosture.REGULAR,
                    18));
        });
        radioButtonExample_italic.selectedProperty().addListener((observable, oldValue, newValue) -> {
            radioButtonExample_text.setFont(Font.font("Verdana",
                    radioButtonExample_bold.isSelected()? FontWeight.BOLD : FontWeight.NORMAL,
                    newValue? FontPosture.ITALIC : FontPosture.REGULAR,
                    18));
        });
    }

    private TextField comboBoxExample_text;
    private ComboBox<String> comboBoxExample_combo;
    @FXML
    private void comboBoxExample(ActionEvent e) throws Exception {
        var dialog = new Dialog();
        dialog.setTitle("ComboBox");
        Parent parent = FXMLLoader.load(getClass().getResource("xml/combo_box_example.fxml"));
        dialog.getDialogPane().setContent(parent);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.show();

        parent.getChildrenUnmodifiable().forEach(node -> {
            switch(node.getId()) {
                case "comboBoxExample_combo":
                    comboBoxExample_combo = (ComboBox<String>) node;
                    break;
                case "comboBoxExample_text":
                    comboBoxExample_text = (TextField) node;
            }
        });

        comboBoxExample_combo.getItems().addAll("bold", "italic");
        comboBoxExample_combo.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            switch(newValue) {
                case "bold":
                    comboBoxExample_text.setFont(Font.font("Verdana", FontWeight.BOLD, 18));
                    break;
                case "italic":
                    comboBoxExample_text.setFont(Font.font("Verdana", FontPosture.ITALIC, 18));
            }
        });
    }

    private TextField listViewExample_text;
    private ListView<String> listViewExample_list;
    @FXML
    private void listViewExample(ActionEvent e) throws Exception {
        var dialog = new Dialog();
        dialog.setTitle("ListView");
        Parent parent = FXMLLoader.load(getClass().getResource("xml/list_view_example.fxml"));
        dialog.getDialogPane().setContent(parent);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.show();

        parent.getChildrenUnmodifiable().forEach(node -> {
            switch(node.getId()) {
                case "listViewExample_list":
                    listViewExample_list = (ListView<String>) node;
                    break;
                case "listViewExample_text":
                    listViewExample_text = (TextField) node;
            }
        });

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
