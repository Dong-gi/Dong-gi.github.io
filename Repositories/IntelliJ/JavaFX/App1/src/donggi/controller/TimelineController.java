package donggi.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.concurrent.atomic.AtomicInteger;

public class TimelineController extends BaseController {

    public TimelineController(Stage stage) {
        super(stage);
    }

    @Override
    public String getDescription() {
        return "타임라인(Timeline) 예제";
    }

    @Override
    public void exec() {
        var num1 = new AtomicInteger(1);
        var num2 = new AtomicInteger(1);

        // Timeline(/* 갱신 주기(fps) */, new KeyFrame(/* 작업 주기(fps) */, /* 작업 */))
        var timelineExample_timeline1 = new Timeline(3, new KeyFrame(Duration.millis(10), e -> print(num1.get() + " : " + num2.getAndIncrement())));
        var timelineExample_timeline2 = new Timeline(2, new KeyFrame(Duration.seconds(1), e -> print(num1.getAndIncrement() + " : " + num2.get())));

        timelineExample_timeline1.setCycleCount(300);
        timelineExample_timeline2.setCycleCount(3);
        timelineExample_timeline1.play();
        timelineExample_timeline2.play();
    }
}

