package ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import javafx.scene.image.Image;

public class MainWindow extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        AnchorPane root = FXMLLoader.load(getClass().getResource("/ui/MainWindow.fxml"));
        Scene scene = new Scene(root);

        // Set the application title
        primaryStage.setTitle("Serial Communication GUI");

        // Set the application icon
        try {
            Image icon = new Image(getClass().getResourceAsStream("/ui/vtv.ico"));
            if (icon != null) {
                primaryStage.getIcons().add(icon);
            } else {
                System.err.println("Icon file not found: /ui/vtv.ico");
            }
        } catch (Exception e) {
            System.err.println("Error loading icon: " + e.getMessage());
        }

        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void launchGUI() {
        launch();
    }
}
