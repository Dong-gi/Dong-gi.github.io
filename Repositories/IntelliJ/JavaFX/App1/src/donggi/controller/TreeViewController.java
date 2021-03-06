package donggi.controller;

import donggi.bean.FileItem;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class TreeViewController extends BaseController {

    public TreeViewController(Stage stage) {
        super(stage);
    }

    @Override
    public String getDescription() {
        return "트리뷰(TreeView) 예제";
    }

    @Override
    public void exec() {
        var dialog = new Dialog();
        dialog.setTitle("트리뷰");
        TreeView<FileItem> parent = null;
        try {
            parent = FXMLLoader.load(getClass().getResource("../fxml/tree_view_example.fxml"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        dialog.getDialogPane().setContent(parent);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL);
        dialog.show();

        parent.setRoot(new TreeItem<>(new FileItem(new File("/"))));
        parent.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue.getValue().getFile().isDirectory()) {
                newValue.getChildren().setAll(Arrays.stream(newValue.getValue().getFile().listFiles()).collect(
                        () -> new ArrayList<>(),
                        (list, file) -> list.add(new TreeItem<FileItem>(new FileItem(file))),
                        (list1, list2) -> list1.addAll(list2)
                ));
            }
        });
    }
}

