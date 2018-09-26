package donggi.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

import java.io.IOException;

public class ImageButtonController extends BaseController {

    public ImageButtonController(Stage stage) {
        super(stage);
    }

    public ImageButtonController() {
    } // FXML 로드하면서 Controller 생성시 필요

    @Override
    public String getDescription() {
        return "이미지 버튼(Button + ImageView) 예제";
    }

    @Override
    public void exec() {
        var dialog = new Dialog();
        try {
            dialog.getDialogPane().setContent(FXMLLoader.load(getClass().getResource("../fxml/image_button_example.fxml")));
        } catch (IOException e) {
            e.printStackTrace();
        }
        dialog.setTitle("이미지 버튼");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK);
        dialog.show();
    }

    @FXML
    private void handle(ActionEvent e) throws Exception {
        var image = new ImageView("http://via.placeholder.com/50x50");
        image.setPreserveRatio(true);
        image.setFitWidth(50.);
        ((Button) e.getSource()).setGraphic(image);
    }
}
