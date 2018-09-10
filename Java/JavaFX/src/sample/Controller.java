package sample;

import javafx.animation.KeyFrame;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.beans.InvalidationListener;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

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

    private ProgressIndicator slider_example_progress_indicator;
    private ProgressBar slider_example_progress_bar;
    private ScrollBar slider_example_scroll_bar;
    private Slider slider_example_slider;
    private Spinner slider_example_spinner;
    @FXML
    private void sliderExample(ActionEvent e) throws Exception {
        var dialog = new Dialog();
        dialog.setTitle("Slider + etc...");
        Parent parent = FXMLLoader.load(getClass().getResource("xml/slider_example.fxml"));
        dialog.getDialogPane().setContent(parent);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.show();

        parent.getChildrenUnmodifiable().forEach(node -> {
            try {
                switch (node.getId()) {
                    case "slider_example_progress_indicator":
                        slider_example_progress_indicator = (ProgressIndicator) node;
                        break;
                    case "slider_example_progress_bar":
                        slider_example_progress_bar = (ProgressBar) node;
                        break;
                    case "slider_example_scroll_bar":
                        slider_example_scroll_bar = (ScrollBar) node;
                        break;
                    case "slider_example_slider":
                        slider_example_slider = (Slider) node;
                        break;
                    case "slider_example_spinner":
                        slider_example_spinner = (Spinner) node;
                        break;
                }
            } catch (NullPointerException ex) {}
        });

        slider_example_spinner.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(0., 1., 0., 0.05));
        slider_example_progress_indicator.progressProperty().bindBidirectional(slider_example_progress_bar.progressProperty());
        slider_example_progress_indicator.progressProperty().bindBidirectional(slider_example_scroll_bar.valueProperty());
        slider_example_progress_indicator.progressProperty().bindBidirectional(slider_example_slider.valueProperty());
        slider_example_progress_indicator.progressProperty().addListener((observable, oldValue, newValue) -> slider_example_spinner.getEditor().setText(String.format("%.2f", newValue.doubleValue())));
        slider_example_spinner.valueProperty().addListener((observable, oldValue, newValue) -> slider_example_progress_indicator.setProgress(Double.valueOf(newValue.toString())));
    }

    private ImageView intersect_example_img1;
    private ImageView intersect_example_img2;
    @FXML
    private void intersectExample(ActionEvent e) throws Exception {
        var dialog = new Dialog();
        dialog.setTitle("Intersects");
        final Parent parent = FXMLLoader.load(getClass().getResource("xml/intersect_example.fxml"));
        dialog.getDialogPane().setContent(parent);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.show();

        parent.getChildrenUnmodifiable().forEach(node -> {
            try {
                switch (node.getId()) {
                    case "intersect_example_img1":
                        intersect_example_img1 = (ImageView) node;
                        break;
                    case "intersect_example_img2":
                        intersect_example_img2 = (ImageView) node;
                        break;
                }
            } catch (NullPointerException ex) {}
        });

        intersect_example_img1.setImage(new Image("http://via.placeholder.com/100x100"));
        intersect_example_img2.setImage(new Image("http://via.placeholder.com/50x50"));
        parent.addEventHandler(MouseEvent.MOUSE_MOVED, event -> {
            intersect_example_img2.setLayoutX(event.getX() - 25);
            intersect_example_img2.setLayoutY(event.getY() - 25);

            if(intersect_example_img1.getBoundsInParent().intersects(intersect_example_img2.getBoundsInParent()))
                parent.setStyle("-fx-background-color: red");
            else
                parent.setStyle("-fx-background-color: white");
        });
    }

    private Timeline timelineExample_timeline1, timelineExample_timeline2;
    private final AtomicInteger timelineExample_num = new AtomicInteger(1);
    @FXML
    private void timelineExample(ActionEvent e) throws Exception {
        final var button = ((Button) e.getSource());
        final var delimiter = " > ";
        if(timelineExample_timeline1 == timelineExample_timeline2) {
            button.setText(button.getText() + delimiter + "0" + delimiter + "0");
            timelineExample_timeline1 = new Timeline(30.
                    , new KeyFrame(Duration.millis(10.), event -> {
                var subs = button.getText().split(delimiter);
                subs[1] = String.valueOf(timelineExample_num.intValue());
                button.setText(String.join(delimiter, subs));
                timelineExample_num.set(timelineExample_num.intValue() + 1);
            }));
            timelineExample_timeline2 = new Timeline(1.
                    , new KeyFrame(Duration.seconds(1.), event -> {
                var subs = button.getText().split(delimiter);
                subs[2] = String.valueOf(timelineExample_num.intValue() / 100);
                button.setText(String.join(delimiter, subs));
            }));
            timelineExample_timeline1.setCycleCount(Integer.MAX_VALUE);
            timelineExample_timeline2.setCycleCount(Integer.MAX_VALUE);
            timelineExample_timeline1.play();
            timelineExample_timeline2.play();
        } else {
            switch(timelineExample_timeline1.getStatus()) {
                case RUNNING: timelineExample_timeline1.pause(); break;
                default: timelineExample_timeline1.play();
            }
            switch(timelineExample_timeline2.getStatus()) {
                case RUNNING: timelineExample_timeline2.pause(); break;
                default: timelineExample_timeline2.play();
            }
        }
    }


    private ParallelTransition parallelTransitionExample_parallel;
    @FXML
    private void parallelTransigionExample(ActionEvent e) throws Exception {
        final var button = ((Button) e.getSource());
        final var delimiter = " > ";
        final var transition = new TranslateTransition(Duration.millis(30), button);
        transition.byXProperty().bind(new ReadOnlyDoubleProperty() {
            @Override
            public Object getBean() {
                return null;
            }

            @Override
            public String getName() {
                return null;
            }

            @Override
            public double get() {
                return Math.sin(timelineExample_num.doubleValue() / 10);
            }

            @Override
            public void addListener(ChangeListener<? super Number> listener) {

            }

            @Override
            public void removeListener(ChangeListener<? super Number> listener) {

            }

            @Override
            public void addListener(InvalidationListener listener) {

            }

            @Override
            public void removeListener(InvalidationListener listener) {

            }
        });

        if(parallelTransitionExample_parallel == null) {
            button.setText(button.getText() + delimiter + "0" + delimiter + "0");
            var timeline1 = new Timeline(30.
                    , new KeyFrame(Duration.millis(10.), event -> {
                var subs = button.getText().split(delimiter);
                subs[1] = String.valueOf(timelineExample_num.intValue());
                subs[2] = String.valueOf(timelineExample_num.intValue() / 100);
                button.setText(String.join(delimiter, subs));
                timelineExample_num.set(timelineExample_num.intValue() + 1);
                transition.play();
            }));
            var timeline2 = new Timeline(1.
                    , new KeyFrame(Duration.seconds(1.), event -> {
                var subs = button.getText().split(delimiter);
                subs[2] = String.valueOf(timelineExample_num.intValue() / 100);
                button.setText(String.join(delimiter, subs));
            }));
            timeline1.setCycleCount(Integer.MAX_VALUE);
            timeline2.setCycleCount(Integer.MAX_VALUE);

            parallelTransitionExample_parallel = new ParallelTransition(
                    timeline1, timeline2
            );
        }

        switch(parallelTransitionExample_parallel.getStatus()) {
            case RUNNING: parallelTransitionExample_parallel.pause(); break;
            default: parallelTransitionExample_parallel.play();
        }
    }


    private final static class MyTreeItem {
        private StringProperty name;
        public void setName(String value) { nameProperty().set(value); }
        public String getName() { return nameProperty().get(); }
        public StringProperty nameProperty() {
            if (name == null) name = new SimpleStringProperty(this, "name");
            return name;
        }

        private LongProperty lastModified;
        public void setLastModified(long value) { lastModifiedProperty().set(value); }
        public long getLastModified() { return lastModifiedProperty().get(); }
        public LongProperty lastModifiedProperty() {
            if (lastModified == null) lastModified = new SimpleLongProperty(this, "lastModified");
            return lastModified;
        }

        private File file;
        public File getFile() { return file; }

        public MyTreeItem(File file) {
            this.file = file;
            try {
                setName(file.getName().length() > 0? file.getName() : file.getCanonicalPath());
            } catch (IOException e) {
                setName(file.getAbsolutePath());
            }
            setLastModified(file.lastModified());
        }

        @Override
        public String toString() { return getName(); }
    }
    @FXML
    private void treeTableViewExample(ActionEvent e) throws Exception {
        var dialog = new Dialog();
        dialog.setTitle("TreeTableView");
        TreeTableView<MyTreeItem> parent = FXMLLoader.load(getClass().getResource("xml/tree_table_view_example.fxml"));
        dialog.getDialogPane().setContent(parent);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.show();

        parent.setRoot(new TreeItem<>(new MyTreeItem(new File("/"))));
        TreeTableColumn<MyTreeItem, String> fileNameCol = new TreeTableColumn<>("File Name");
        fileNameCol.setPrefWidth(200);
        TreeTableColumn<MyTreeItem, Long> lastModifiedCol = new TreeTableColumn<>("Last Modified");
        parent.getColumns().setAll(fileNameCol, lastModifiedCol);

        fileNameCol.setCellValueFactory(param -> param.getValue().getValue().nameProperty());
        lastModifiedCol.setCellValueFactory(param -> {
            final var p = param;
            return new ObservableValue<Long>() {
                @Override
                public void addListener(ChangeListener<? super Long> listener) {

                }

                @Override
                public void removeListener(ChangeListener<? super Long> listener) {

                }

                @Override
                public Long getValue() {
                    return p.getValue().getValue().getLastModified();
                }

                @Override
                public void addListener(InvalidationListener listener) {

                }

                @Override
                public void removeListener(InvalidationListener listener) {

                }
            };
        });

        parent.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        parent.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue.getValue().getFile().isDirectory()) {
                newValue.getChildren().setAll(Arrays.stream(newValue.getValue().getFile().listFiles()).collect(
                        () -> new ArrayList<>(),
                        (list, file) -> list.add(new TreeItem<MyTreeItem>(new MyTreeItem(file))),
                        (list1, list2) -> list1.addAll(list2)
                ));
            }
        });


        var dialog2 = new Dialog();
        dialog2.setTitle("TreeView");
        TreeView<MyTreeItem> parent2 = FXMLLoader.load(getClass().getResource("xml/tree_view_example.fxml"));
        dialog2.getDialogPane().setContent(parent2);
        dialog2.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog2.show();

        parent2.setRoot(new TreeItem<>(new MyTreeItem(new File("/"))));
        parent2.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue.getValue().getFile().isDirectory()) {
                newValue.getChildren().setAll(Arrays.stream(newValue.getValue().getFile().listFiles()).collect(
                        () -> new ArrayList<>(),
                        (list, file) -> list.add(new TreeItem<MyTreeItem>(new MyTreeItem(file))),
                        (list1, list2) -> list1.addAll(list2)
                ));
            }
        });
    }


}
