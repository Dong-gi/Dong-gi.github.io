package donggi.controller;

import donggi.bean.FileItem;
import javafx.collections.FXCollections;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class TableViewController extends BaseController {

    public TableViewController(Stage stage) {
        super(stage);
    }

    @Override
    public String getDescription() {
        return "테이블뷰(TableView) 예제";
    }

    @Override
    public void exec() {
        var dialog = new Dialog();
        dialog.setTitle("테이블뷰");
        TableView<FileItem> parent = null;
        try {
            parent = FXMLLoader.load(getClass().getResource("../fxml/table_view_example.fxml"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        dialog.getDialogPane().setContent(parent);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL);
        dialog.show();

        parent.setItems(FXCollections.observableArrayList(Arrays.stream(new File("/").listFiles()).map(file -> new FileItem(file)).toArray(FileItem[]::new)));
        TableColumn<FileItem, String> fileNameCol = new TableColumn<>("File Name");
        fileNameCol.setPrefWidth(200);
        TableColumn<FileItem, Number> lastModifiedCol = new TableColumn<>("Last Modified");
        parent.getColumns().setAll(fileNameCol, lastModifiedCol);

        fileNameCol.setCellValueFactory(new PropertyValueFactory<FileItem, String>("Name"));
        lastModifiedCol.setCellValueFactory(new PropertyValueFactory<FileItem, Number>("LastModified"));

        parent.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        //parent.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
    }
}

