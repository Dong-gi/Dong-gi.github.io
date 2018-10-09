package donggi.controller;

import javafx.event.EventHandler;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class MouseEventController extends BaseController {

    private Dialog dialog;
    private final Circle circle = new Circle(250, 250, 30);
    private final EventHandler<MouseEvent> mouseClickHandler = new EventHandler<>() {
        final ScheduledExecutorService service = Executors.newScheduledThreadPool(5);
        ScheduledFuture leftButtonClick;

        @Override
        public void handle(MouseEvent mouseEvent) {
            switch(mouseEvent.getButton()) {
                case PRIMARY:
                    if(leftButtonClick == null ||
                            leftButtonClick.isDone())
                        leftButtonClick = service.schedule(MouseEventController.this::onMouseLeftButtonClick, 300, TimeUnit.MILLISECONDS);
                    else {
                        leftButtonClick.cancel(true);
                        service.schedule(MouseEventController.this.onMouseLeftButtonDoubleClick(mouseEvent), 0, TimeUnit.MILLISECONDS);
                    }
                    return;
                case SECONDARY:
                    service.schedule(MouseEventController.this::onMouseRightButtonClick, 0, TimeUnit.MILLISECONDS);
                    return;
            }
        }
    };

    public MouseEventController(Stage stage) {
        super(stage);
    }

    public MouseEventController() {
    } // FXML 로드하면서 Controller 생성시 필요

    @Override
    public String getDescription() {
        return "마우스 이벤트 예제";
    }

    @Override
    public void exec() {
        dialog = new Dialog();
        dialog.getDialogPane().getChildren().add(circle);
        dialog.getDialogPane().setMinSize(500, 500);
        dialog.setTitle("마우스 이벤트");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK);
        dialog.show();

        dialog.getDialogPane().setOnMouseClicked(this.mouseClickHandler);
    }

    private void onMouseLeftButtonClick() {
        circle.setFill(Color.RED);
        dialog.getDialogPane().requestLayout();
        print("좌클릭 처리");
    }

    private Runnable onMouseLeftButtonDoubleClick(MouseEvent mouseEvent) {
        return () -> {
            circle.setCenterX(mouseEvent.getX());
            circle.setCenterY(mouseEvent.getY());
            print("더블클릭 처리");
        };
    }

    private void onMouseRightButtonClick() {
        circle.setFill(Color.BLUE);
        dialog.getDialogPane().requestLayout();
        print("우클릭 처리");
    }

}
