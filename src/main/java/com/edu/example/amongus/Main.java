package com.edu.example.amongus;

import com.edu.example.amongus.logic.GameConfig;
import com.edu.example.amongus.net.GameClient;
import com.edu.example.amongus.ui.MatchUI;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Main extends Application {
    private static Stage primaryStage;
    private static GameApp game;

    private static MatchUI matchUI; // ✅ 成员变量

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    /** 切换到游戏场景 */
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

    public static void joinGame() {
        // 1. 获取玩家信息
        String playerName = GameConfig.getPlayerName();
        String playerColor = GameConfig.getPlayerColor();

        // 2. 加载玩家图片
        Image playerImage = new Image(Main.class.getResourceAsStream(
                "/com/edu/example/amongus/images/" + playerColor + ".png"));

        // 3. 加载地图碰撞图片
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

        // 7. 切换到匹配界面
        MatchUI matchUI = new MatchUI(5); // 假设 MAX_PLAYERS=5

        int currentPlayers = game.getCurrent(); // 或从 GameApp/GameClient 拿
        System.out.println("currentPlayers: " + currentPlayers);
        matchUI.onMatchUpdate(currentPlayers, 5);

// 注册监听器，网络层收到 MATCH_UPDATE 时更新 UI
        GameApp.setMatchUpdateListener((current, total) -> {
            matchUI.onMatchUpdate(current, total);
        });

        Scene matchScene = new Scene(matchUI, 800, 600);
        primaryStage.setScene(matchScene);
    }


    public static void main(String[] args) {
        launch(args);
    }
}
