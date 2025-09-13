package com.edu.example.amongus;

import javafx.application.Application;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.io.IOException;

public class Main extends Application {
    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;

        // 先加载 StartMenu.fxml
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/edu/example/amongus/start_menu.fxml"));
        Scene startScene = new Scene(loader.load(), 800, 600);

        stage.setTitle("Among Us Demo");
        stage.setScene(startScene);
        stage.show();
    }

    /** 切换到游戏场景 */
    public static void startGame() {
        try {
            Pane root = new Pane();
            GameApp game = new GameApp(root);

            Scene gameScene = new Scene(root, 800, 600);
            game.handleInput(gameScene);

            primaryStage.setScene(gameScene);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
