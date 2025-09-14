package com.edu.example.amongus;

import com.edu.example.amongus.logic.GameConfig;
import javafx.application.Application;
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

public class Main extends Application {
    private static Stage primaryStage;
    private static GameApp game;

    public static void startGameScene() {
        if (game == null) return;

        Pane root = game.getGamePane();
        Scene gameScene = new Scene(root, 800, 600);

        // 让 GameApp 开始监听输入
        game.handleInput(gameScene);

        primaryStage.setScene(gameScene);
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

    /** 玩家点击“加入游戏”按钮时调用 */
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
        double startX = 100;
        double startY = 100;

        // 5. 创建玩家对象
        Player myPlayer = new Player(startX, startY, playerImage, collisionReader);

        // 6. 创建 GameApp（但不切换场景，等服务器下发 GAME_START）
        game = new GameApp(new Pane(), myPlayer);

        //GameApp 会在收到服务器 GAME_START 消息后显示玩家和地图
    }


    public static void main(String[] args) {
        launch(args);
    }
}
