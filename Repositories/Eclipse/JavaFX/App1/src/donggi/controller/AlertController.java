package donggi.controller;

import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.util.Arrays;

public class AlertController extends BaseController {

    public AlertController(Stage stage) {
        super(stage);
    }

    @Override
    public String getDescription() {
        return "알림(Alert) 예제";
    }

    @Override
    public void exec() {
        print(Arrays.toString(Alert.AlertType.values()));
        var alert = new Alert(Alert.AlertType.INFORMATION);
        alert.titleProperty().bind(getStage().titleProperty());
        alert.setHeaderText("이것이 알림창이다");
        alert.setContentText("부모 윈도우를 클릭해보세요");
        alert.show();
    }
}
