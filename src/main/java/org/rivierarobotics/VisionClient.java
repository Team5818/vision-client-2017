package org.rivierarobotics;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class VisionClient extends Application {

    public static void main(String[] args) {
        Application.launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("main.fxml"));
        loader.setController(new VisionController());
        Parent parent = loader.load();
        primaryStage.setScene(new Scene(parent));
        primaryStage.show();
        primaryStage.setMaximized(true);
        primaryStage.centerOnScreen();
    }

}
