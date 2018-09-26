package donggi.controller;

import javafx.animation.KeyFrame;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.concurrent.atomic.AtomicInteger;

public class ParallelTransitionController extends BaseController {

    public ParallelTransitionController(Stage stage) {
        super(stage);
    }

    @Override
    public String getDescription() {
        return "병행작업(ParallelTransition) 예제";
    }

    @Override
    public void exec() {
        var num1 = new AtomicInteger(1);
        var num2 = new AtomicInteger(1);

        var timeline1 = new Timeline(30, new KeyFrame(Duration.millis(10), e -> print(num1.get() + " : " + num2.getAndIncrement())));
        var timeline2 = new Timeline(1, new KeyFrame(Duration.seconds(1), e -> print(num1.getAndIncrement() + " : " + num2.get())));
        timeline1.setCycleCount(300);
        timeline2.setCycleCount(3);

        var parallelTransition = new ParallelTransition(
                timeline1, timeline2
        );
        parallelTransition.play();
    }
}

