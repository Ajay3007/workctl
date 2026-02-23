package com.workctl.gui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

public class WorkctlApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/workctl/gui/view/main.fxml"));

        javafx.geometry.Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        double w = Math.min(1200, bounds.getWidth() * 0.9);
        double h = Math.min(800, bounds.getHeight() * 0.9);

        Scene scene = new Scene(loader.load(), w, h);
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
