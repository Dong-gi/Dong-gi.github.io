package donggi.controller;

import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;

public class ProgressController extends BaseController {

    private ProgressIndicator slider_example_progress_indicator;
    private ProgressBar slider_example_progress_bar;
    private ScrollBar slider_example_scroll_bar;
    private Slider slider_example_slider;
    private Spinner slider_example_spinner;

    public ProgressController(Stage stage) {
        super(stage);
    }

    public ProgressController() {
    } // FXML 로드하면서 Controller 생성시 필요

    @Override
    public String getDescription() {
        return "진행도(ProgressIndicator+ProgressBar+ScrollBar+Slider+Spinner) 예제";
    }

    @Override
    public void exec() {
        var dialog = new Dialog();
        try {
            dialog.getDialogPane().setContent(FXMLLoader.load(getClass().getResource("../fxml/progress_example.fxml")));
        } catch (IOException e) {
        }
        dialog.setTitle("진행도");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL);
        dialog.show();

        slider_example_progress_indicator = (ProgressIndicator) dialog.getDialogPane().lookup("#slider_example_progress_indicator");
        slider_example_progress_bar = (ProgressBar) dialog.getDialogPane().lookup("#slider_example_progress_bar");
        slider_example_scroll_bar = (ScrollBar) dialog.getDialogPane().lookup("#slider_example_scroll_bar");
        slider_example_slider = (Slider) dialog.getDialogPane().lookup("#slider_example_slider");
        slider_example_spinner = (Spinner) dialog.getDialogPane().lookup("#slider_example_spinner");

        slider_example_spinner.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(0., 1., 0., 0.05));
        slider_example_progress_indicator.progressProperty().bindBidirectional(slider_example_progress_bar.progressProperty());
        slider_example_progress_indicator.progressProperty().bindBidirectional(slider_example_scroll_bar.valueProperty());
        slider_example_progress_indicator.progressProperty().bindBidirectional(slider_example_slider.valueProperty());
        slider_example_progress_indicator.progressProperty().addListener((observable, oldValue, newValue) -> slider_example_spinner.getEditor().setText(String.format("%.2f", newValue.doubleValue())));
        slider_example_spinner.valueProperty().addListener((observable, oldValue, newValue) -> slider_example_progress_indicator.setProgress(Double.valueOf(newValue.toString())));
    }
}
