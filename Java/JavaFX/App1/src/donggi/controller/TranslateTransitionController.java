package donggi.controller;

import javafx.animation.TranslateTransition;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class TranslateTransitionController extends BaseController {

    public TranslateTransitionController(Stage stage) {
        super(stage);
    }

    public TranslateTransitionController() {
    } // FXML 로드하면서 Controller 생성시 필요

    @Override
    public String getDescription() {
        return "노드 이동(TranslateTransition) 예제";
    }

    @Override
    public void exec() {
        var dialog = new Dialog();
        var circle = new Circle(250, 250, 30);
        dialog.getDialogPane().getChildren().add(circle);
        dialog.getDialogPane().setMinSize(500, 500);
        dialog.setTitle("노드 이동");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK);
        dialog.show();

        final var transition = new TranslateTransition(Duration.millis(100), circle);
        final var random = new Random();

        dialog.getDialogPane().addEventHandler(MouseEvent.MOUSE_MOVED, event -> {
            transition.setByX((random.nextDouble()-0.5) * 100);
            transition.setByY((random.nextDouble()-0.5) * 100);
            transition.play();
        });
    }

}
