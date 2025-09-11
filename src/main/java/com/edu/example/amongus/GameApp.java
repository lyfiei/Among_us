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

    public GameApp(Pane pane) {
        this.gamePane = pane;
        this.inputHandler = new InputHandler();

        try {
            Image mapImage = new Image(getClass().getResourceAsStream("/com/edu/example/amongus/images/game_map.png"));
            Image collisionImage = new Image(getClass().getResourceAsStream("/com/edu/example/amongus/images/collision_map.png"));
            Image playerImage = new Image(getClass().getResourceAsStream("/com/edu/example/amongus/images/player.png"));

            gameMap = new Map(mapImage, collisionImage);
            player = new Player(100, 100, playerImage, gameMap.getCollisionReader());

            gamePane.getChildren().add(gameMap.getMapView());
            gamePane.getChildren().add(player.getView());

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("资源加载失败");
        }
    }

    public void handleInput(Scene scene) {
        scene.setOnKeyPressed(e -> inputHandler.press(e.getCode()));
        scene.setOnKeyReleased(e -> inputHandler.release(e.getCode()));

        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                double dx = 0, dy = 0;
                if (inputHandler.isPressed(KeyCode.UP)) dy -= GameConstants.MOVEMENT_SPEED;
                if (inputHandler.isPressed(KeyCode.DOWN)) dy += GameConstants.MOVEMENT_SPEED;
                if (inputHandler.isPressed(KeyCode.LEFT)) dx -= GameConstants.MOVEMENT_SPEED;
                if (inputHandler.isPressed(KeyCode.RIGHT)) dx += GameConstants.MOVEMENT_SPEED;
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
