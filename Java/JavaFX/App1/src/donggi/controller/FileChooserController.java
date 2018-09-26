package donggi.controller;

import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileChooserController extends BaseController {

    private Stage mainStage  = null;

    public FileChooserController(Stage stage) {
        super(stage);
    }

    @Override
    public String getDescription() {
        return "파일 선택(FileChooser) 예제";
    }

    @Override
    public void exec() {
        mainStage = new Stage();
        var buttonPane = new HBox();
        var selectBtn = new Button("파일 선택");
        buttonPane.getChildren().add(selectBtn);
        selectBtn.setOnAction(e->directoryList());

        mainStage.setTitle("파일 선택 + 랜덤 액세스 파일");
        mainStage.setScene(new Scene(buttonPane, 400, 40));
        mainStage.show();
    }

    private void directoryList(){
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("파일 열기");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("이미지", "*.png", "*.jpg", "*.gif"),
                new FileChooser.ExtensionFilter("음악", "*.wav", "*.mp3", "*.aac"),
                new FileChooser.ExtensionFilter("모든 파일", "*.*"));
        File selectedFile = fileChooser.showOpenDialog(mainStage);

        if(selectedFile == null) return;
        try {
            RandomAccessFile fin = new RandomAccessFile(selectedFile, "r");
            FileChannel channelIn = fin.getChannel();
            var newFile = new File(selectedFile.getParentFile(), "new " + selectedFile.getName());
            newFile.createNewFile();
            RandomAccessFile fout = new RandomAccessFile(newFile, "rw");
            fout.setLength(fin.length());
            FileChannel channelOut = fout.getChannel();

            final int segmentSize = 8*1024*1024;
            final long segmentNum = selectedFile.length() / segmentSize + (
                    (selectedFile.length() % segmentSize == 0)? 0 : 1);
            var list = Stream.iterate(0, x -> x+1).limit(segmentNum).collect(Collectors.toList());
            Collections.shuffle(list);

            for(Integer i : list) {
                ByteBuffer buffer = ByteBuffer.allocateDirect(segmentSize);
                buffer.clear();
                fin.seek(i*segmentSize);
                channelIn.read(buffer);
                buffer.flip();
                fout.seek(i*segmentSize);
                channelOut.write(buffer);
            }
            channelIn.close();
            channelOut.close();
            fin.close();
            fout.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
