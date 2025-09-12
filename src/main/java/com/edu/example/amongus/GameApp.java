package com.edu.example.amongus;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;

import java.util.UUID;

public class GameApp {
    private final Pane gamePane;
    private final Player player;
    private final Mapp gameMap;
    private final InputHandler inputHandler;
    private final TaskManager taskManager;

    private CardSwipeTask cardTask;
    private DownloadTask downloadTask;

    // 玩家唯一 id
    private final String myId;
    private final String myNick;

    public GameApp(Pane pane) {
        this.gamePane = pane;
        this.inputHandler = new InputHandler();
        this.taskManager = new TaskManager();

        this.myId = UUID.randomUUID().toString();
        this.myNick = "P" + myId.substring(0, 4);

        try {
            Image mapImage = new Image(getClass().getResourceAsStream("/com/edu/example/amongus/images/map1.png"));
            Image collisionImage = new Image(getClass().getResourceAsStream("/com/edu/example/amongus/images/map2.jpg"));
            Image playerImage = new Image(getClass().getResourceAsStream("/com/edu/example/amongus/images/green.png"));

            gameMap = new Mapp(mapImage, collisionImage);
            player = new Player(1650, 500, playerImage, gameMap.getCollisionReader());

            gamePane.getChildren().add(gameMap.getMapView());
            gamePane.getChildren().add(player.getView());

            // 初始化任务
            cardTask = new CardSwipeTask(gamePane);
            cardTask.setTaskCompleteListener(success ->
                    System.out.println("刷卡完成，成功=" + success)
            );
            taskManager.addTask(cardTask);

            downloadTask = new DownloadTask();
            taskManager.addTask(downloadTask);

        } catch (Exception e) {
            throw new RuntimeException("资源加载失败", e);
        }
    }

    public void handleInput(Scene scene) {
        scene.setOnKeyPressed(e -> {
            inputHandler.press(e.getCode());

            // 保留原来的按键触发任务
            if (e.getCode() == KeyCode.T && !cardTask.isActive()) {
                cardTask.start();
            }
            if (e.getCode() == KeyCode.F && !downloadTask.isActive()) {
                downloadTask.start();
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

                if (dx != 0 && dy != 0) { // 斜向速度处理
                    dx /= Math.sqrt(2);
                    dy /= Math.sqrt(2);
                }

                player.move(dx, dy);

                // 摄像机跟随
                double offsetX = -player.getX() + getSceneWidth() / 2 - GameConstants.PLAYER_SIZE / 2;
                double offsetY = -player.getY() + getSceneHeight() / 2 - GameConstants.PLAYER_SIZE / 2;
                gameMap.getMapView().setX(offsetX);
                gameMap.getMapView().setY(offsetY);
                player.getView().setX(player.getX() + offsetX);
                player.getView().setY(player.getY() + offsetY);

                // 暂时空着的玩家位置触发任务
                taskManager.checkTasks(player.getX(), player.getY());
            }
        };
        timer.start();
    }

    private double getSceneWidth() {
        return (gamePane.getScene() != null && gamePane.getScene().getWidth() > 0) ? gamePane.getScene().getWidth() : 800;
    }

    private double getSceneHeight() {
        return (gamePane.getScene() != null && gamePane.getScene().getHeight() > 0) ? gamePane.getScene().getHeight() : 600;
    }

    public boolean allTasksCompleted() {
        return taskManager.allCompleted();
    }
}
