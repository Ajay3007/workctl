package com.workctl.gui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class WorkctlApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/workctl/gui/view/main.fxml")
        );

        Scene scene = new Scene(loader.load(), 1200, 800);
        scene.getStylesheets().add(
                WorkctlApp.class.getResource(
                        com.workctl.gui.ThemeManager.cssPath()).toExternalForm());

        stage.setTitle("workctl");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
