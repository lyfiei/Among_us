package com.edu.example.amongus;

import com.edu.example.amongus.logic.GameConfig;
import com.edu.example.amongus.net.GameClient;
import com.edu.example.amongus.ui.MatchUI;
import com.edu.example.amongus.ui.StartMenuController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.layout.Pane;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.animation.FadeTransition;
import javafx.util.Duration;
import javafx.fxml.FXMLLoader;
import java.io.IOException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Main extends Application {
    private static Stage primaryStage;
    private static GameApp game;

    private static StackPane root;

    private static MatchUI matchUI; // ✅ 成员变量
    public static MediaPlayer startMenuPlayer;
    static StartMenuController controller;

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    @Override
    public void start(Stage stage) throws Exception {

        // 先加载自定义字体
        Font.loadFont(getClass().getResourceAsStream("/fonts/ZCOOLKuHei-Regular.ttf"), 16);
        Font.loadFont(getClass().getResourceAsStream("/fonts/LuckiestGuy-Regular.ttf"), 16);

        primaryStage = stage;

        // 创建启动画面 ImageView
        Image splashImage = new Image(getClass().getResourceAsStream(
                "/com/edu/example/amongus/images/splash.png"));
        ImageView splashView = new ImageView(splashImage);
        splashView.setFitWidth(1000);
        splashView.setFitHeight(600);

        StackPane splashRoot = new StackPane(splashView);
        splashRoot.setStyle("-fx-background-color: black;");
        Scene splashScene = new Scene(splashRoot, 1000, 600);
        stage.setScene(splashScene);
        stage.setTitle("Among Us Demo");
        stage.show();

        // 淡入效果
        FadeTransition fadeIn = new FadeTransition(Duration.seconds(1.5), splashView);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        // 淡出效果
        FadeTransition fadeOut = new FadeTransition(Duration.seconds(1.5), splashView);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setDelay(Duration.seconds(3));

        // 淡出结束后切换场景
        fadeOut.setOnFinished(event -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(
                        "/com/edu/example/amongus/start_menu.fxml"));
                StackPane startRoot = loader.load();  // ← 这里赋值给局部变量
                controller = loader.getController();
                Scene startScene = new Scene(startRoot, 1000, 600);

                // 加载全局 CSS
                startScene.getStylesheets().add(getClass().getResource(
                        "/com/edu/example/amongus/styles/global.css").toExternalForm());


                stage.setScene(startScene);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        fadeIn.play();
        fadeOut.play();
    }


    // 设置 Stage 的方法
    public static void setPrimaryStage(Stage stage) {
        primaryStage = stage;
    }

    /**
     * 玩家点击"加入游戏"按钮后调用此方法。
     * 负责显示匹配界面，并开始连接服务器。
     */
    public static void joinGame() {
            String playerName = GameConfig.getPlayerName();
            String playerColor = GameConfig.getPlayerColor();

            Image playerImage = new Image(Main.class.getResourceAsStream(
                    "/com/edu/example/amongus/images/" + playerColor + ".png"));
            Image collisionImage = new Image(Main.class.getResourceAsStream(
                    "/com/edu/example/amongus/images/map2.jpg"));
            PixelReader collisionReader = collisionImage.getPixelReader();
// 4. 设置默认初始坐标（以后可以改为服务器分配）
            double startX = 1650;
            double startY = 500;
// 5. 创建玩家对象
            Player myPlayer = new Player(startX, startY, playerImage, collisionReader);
// 6. 创建 GameApp（先不显示）
            game = new GameApp(new Pane());
        Pane root = new Pane();
        // 8. 创建 Scene 并显示
        Scene gameScene = new Scene(root);
        primaryStage.setScene(gameScene);
        primaryStage.show();
// 切换场景前先停止之前的视频
        controller.stopVideo();
// 7. 切换到匹配界面
        MatchUI matchUI = new MatchUI(5); // 假设 MAX_PLAYERS=5
// 注册监听器，网络层收到 MATCH_UPDATE 时更新 UI
        GameApp.setMatchUpdateListener((current, total) -> {
            matchUI.onMatchUpdate(current, total);

        });
        Scene matchScene = new Scene(matchUI, 800, 600);
        primaryStage.setScene(matchScene);
    }

    /**
     * 匹配成功后，切换到游戏场景。
     * 此方法应由匹配监听器触发，确保在正确时机调用。
     */
    public static void startGameScene() {
        if (game == null) return;
        System.out.println("startGameScene called");
        Pane root = game.getGamePane();
        Scene gameScene = new Scene(root, 800, 600);

// 让 GameApp 开始监听输入
        game.handleInput(gameScene);
        primaryStage.setScene(gameScene);
        primaryStage.show();
        Platform.runLater(() -> {
            gameScene.getRoot().setFocusTraversable(true);
            gameScene.getRoot().requestFocus();
            System.out.println("focus ok? " + gameScene.getRoot().isFocused());
        });
    }



    public static void main(String[] args) {
        launch(args);
    }
}
