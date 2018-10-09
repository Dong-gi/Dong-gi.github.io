package donggi;

import donggi.controller.BaseController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.stage.Stage;

import java.io.File;
import java.util.*;

public class Main extends Application {

    private static Map<String, BaseController> examples;

    @Override
    public void init() {
        examples = new HashMap<>();
        for (var file : new File("./src/donggi/controller")
                .listFiles((dir, name) -> !name.contains(("BaseController"))))
            examples.put(file.getName().split("\\.")[0], null);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("fxml/main_scene.fxml"));
        primaryStage.setTitle("JavaFX 예제");
        primaryStage.setScene(new Scene(root, 600, 300));

        final var listView = ((ListView) root.lookup("#list"));
        var keyList = new LinkedList<String>();
        keyList.addAll(examples.keySet());
        Collections.sort(keyList);
        for(var key : keyList) {
            var controller = (BaseController) Class.forName("donggi.controller." + key).getDeclaredConstructors()[0].newInstance(primaryStage);
            listView.getItems().add(controller.getDescription());
            examples.put(controller.getDescription(), controller);
        }
        listView.getSelectionModel().selectedItemProperty().addListener((observableValue, old, now) -> examples.get(now).exec());

        primaryStage.show();
    }


    public static void main(String[] args) {
        launch(args);
    }
}
