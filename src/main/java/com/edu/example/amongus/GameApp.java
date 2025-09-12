package com.edu.example.amongus;

import javafx.animation.AnimationTimer;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.scene.input.KeyCode;

public class GameApp {
    private final Pane gamePane;
    private final Player player;
    private final Map gameMap;
    private final InputHandler inputHandler;

    private CardSwipeTask cardTask; // 刷卡任务
    private boolean taskActive = false; // 是否正在进行任务

    public GameApp(Pane pane) {
        this.gamePane = pane;
        this.inputHandler = new InputHandler();

        try {
            Image mapImage = new Image(getClass().getResourceAsStream("/com/edu/example/amongus/images/map1.png"));
            Image collisionImage = new Image(getClass().getResourceAsStream("/com/edu/example/amongus/images/map2.jpg"));
            Image playerImage = new Image(getClass().getResourceAsStream("/com/edu/example/amongus/images/green.png"));

            gameMap = new Map(mapImage, collisionImage);
            player = new Player(1650, 500, playerImage, gameMap.getCollisionReader());

            gamePane.getChildren().add(gameMap.getMapView());
            gamePane.getChildren().add(player.getView());
// 初始化刷卡任务
            cardTask = new CardSwipeTask(gamePane);
            cardTask.setTaskCompleteListener(success -> {
                System.out.println("刷卡完成，成功=" + success);
                // success 是回调传入的 boolean，不需要自己再定义
                if (success) {
                    // TODO: 游戏内任务完成逻辑
                } else {
                    // TODO: 刷卡失败逻辑
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("资源加载失败");
        }
    }

    public void handleInput(Scene scene) {
        scene.setOnKeyPressed(e -> {
            inputHandler.press(e.getCode());

            // 按 T 打开刷卡任务
            if (e.getCode() == KeyCode.T && !taskActive) {
                taskActive = true;
                cardTask.start();
            }
            if (e.getCode() == KeyCode.F) {
                new DownloadTask().start();
            }
        });

        scene.setOnKeyReleased(e -> inputHandler.release(e.getCode()));

        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                double dx = 0, dy = 0;

                if (inputHandler.isPressed(KeyCode.UP)) dy -= GameConstants.MOVEMENT_SPEED;
                if (inputHandler.isPressed(KeyCode.DOWN)) dy += GameConstants.MOVEMENT_SPEED;
                if (inputHandler.isPressed(KeyCode.LEFT)) dx -= GameConstants.MOVEMENT_SPEED;
                if (inputHandler.isPressed(KeyCode.RIGHT)) dx += GameConstants.MOVEMENT_SPEED;

                // 斜向移动时保持速度一致
                if (dx != 0 && dy != 0) {
                    dx /= Math.sqrt(2);
                    dy /= Math.sqrt(2);
                }

                player.move(dx, dy);

                // 摄像机跟随玩家
                double offsetX = -player.getX() + scene.getWidth() / 2 - GameConstants.PLAYER_SIZE / 2;
                double offsetY = -player.getY() + scene.getHeight() / 2 - GameConstants.PLAYER_SIZE / 2;
                gameMap.getMapView().setX(offsetX);
                gameMap.getMapView().setY(offsetY);
                player.getView().setX(player.getX() + offsetX);
                player.getView().setY(player.getY() + offsetY);
            }
        };
        timer.start();
    }
}
