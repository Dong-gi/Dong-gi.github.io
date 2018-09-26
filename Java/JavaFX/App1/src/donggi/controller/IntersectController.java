package donggi.controller;

import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

import java.io.IOException;

public class IntersectController extends BaseController {

    private ImageView intersect_example_img1;
    private ImageView intersect_example_img2;

    public IntersectController(Stage stage) {
        super(stage);
    }

    public IntersectController() {
    } // FXML 로드하면서 Controller 생성시 필요

    @Override
    public String getDescription() {
        return "영역 겹침(intersects) 예제";
    }

    @Override
    public void exec() {
        var dialog = new Dialog();
        try {
            dialog.getDialogPane().setContent(FXMLLoader.load(getClass().getResource("../fxml/intersect_example.fxml")));
        } catch (IOException e) {
        }
        dialog.setTitle("영역 겹침");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL);
        dialog.show();

        intersect_example_img1 = (ImageView) dialog.getDialogPane().lookup("#intersect_example_img1");
        intersect_example_img2 = (ImageView) dialog.getDialogPane().lookup("#intersect_example_img2");

        intersect_example_img1.setImage(new Image("http://via.placeholder.com/100x100"));
        intersect_example_img2.setImage(new Image("http://via.placeholder.com/50x50"));
        dialog.getDialogPane().addEventHandler(MouseEvent.MOUSE_MOVED, event -> {
            intersect_example_img2.setLayoutX(event.getX() - 25);
            intersect_example_img2.setLayoutY(event.getY() - 25);

            if (intersect_example_img1.getBoundsInParent().intersects(intersect_example_img2.getBoundsInParent()))
                dialog.getDialogPane().setStyle("-fx-background-color: red");
            else
                dialog.getDialogPane().setStyle("-fx-background-color: white");
        });
    }
}
