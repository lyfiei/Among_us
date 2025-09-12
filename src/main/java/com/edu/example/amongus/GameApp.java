package com.edu.example.amongus;

import com.edu.example.amongus.net.GameClient;
import com.edu.example.amongus.net.Message;
import com.edu.example.amongus.task.CardSwipeTask;
import com.edu.example.amongus.task.DownloadTask;
import com.edu.example.amongus.task.TaskManager;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GameApp {
    private final Pane gamePane;
    private final Player player;
    private final Mapp gameMap;
    private final InputHandler inputHandler;

    // 任务管理
    private final TaskManager taskManager;
    private CardSwipeTask cardTask;
    private DownloadTask downloadTask;

    private final Canvas fogCanvas; // 保留迷雾画布

    // 网络
    private GameClient client;
    private final String myId;
    private final String myNick;
    private final String myColor = "green";

    // 远端玩家
    private final Map<String, RemotePlayer> remotePlayers = new HashMap<>();

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
            cardTask.setTaskCompleteListener(success -> System.out.println("刷卡完成: " + success));
            taskManager.addTask(cardTask);

            downloadTask = new DownloadTask();
            taskManager.addTask(downloadTask);

            // 迷雾画布
            fogCanvas = new Canvas(GameConstants.MAP_WIDTH, GameConstants.MAP_HEIGHT);
            gamePane.getChildren().add(fogCanvas);

        } catch (Exception e) {
            throw new RuntimeException("资源加载失败", e);
        }

        // 尝试连接服务器
        try {
            this.client = new GameClient("127.0.0.1", 55555, parsed -> Platform.runLater(() -> handleNetworkMessage(parsed)));

            Map<String, String> payload = new HashMap<>();
            payload.put("id", myId);
            payload.put("nick", myNick);
            payload.put("color", myColor);
            payload.put("x", String.valueOf(player.getX()));
            payload.put("y", String.valueOf(player.getY()));
            client.send("JOIN", payload);

            System.out.println("Connected to server as " + myNick + " (" + myId + ")");
        } catch (IOException ex) {
            System.out.println("无法连接服务器（离线模式）: " + ex.getMessage());
            this.client = null;
        }
    }

    public void handleInput(Scene scene) {
        scene.setOnKeyPressed(e -> {
            inputHandler.press(e.getCode());

            // 保留原来的 T/F 按键触发任务
            if (e.getCode() == KeyCode.T && !cardTask.isActive()) cardTask.start();
            if (e.getCode() == KeyCode.F && !downloadTask.isActive()) downloadTask.start();
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

                if (dx != 0 && dy != 0) { dx /= Math.sqrt(2); dy /= Math.sqrt(2); }

                player.move(dx, dy);

                if (client != null && (dx != 0 || dy != 0)) {
                    Map<String, String> payload = new HashMap<>();
                    payload.put("id", myId);
                    payload.put("x", String.valueOf(player.getX()));
                    payload.put("y", String.valueOf(player.getY()));
                    try { client.send("MOVE", payload); } catch (IOException ex) { System.out.println("发送 MOVE 失败: " + ex.getMessage()); }
                }

                double offsetX = -player.getX() + getSceneWidth() / 2 - GameConstants.PLAYER_SIZE / 2;
                double offsetY = -player.getY() + getSceneHeight() / 2 - GameConstants.PLAYER_SIZE / 2;
                gameMap.getMapView().setX(offsetX);
                gameMap.getMapView().setY(offsetY);
                player.getView().setX(player.getX() + offsetX);
                player.getView().setY(player.getY() + offsetY);

                // 更新远端玩家
                for (RemotePlayer rp : remotePlayers.values()) {
                    rp.view.setX(rp.x + offsetX);
                    rp.view.setY(rp.y + offsetY);
                }

                // 可以在这里更新任务触发（暂时空着）
                // taskManager.checkTasks(player.getX(), player.getY());
            }
        };
        timer.start();
    }

    private double getSceneWidth() { return (gamePane.getScene() != null && gamePane.getScene().getWidth() > 0) ? gamePane.getScene().getWidth() : 800; }
    private double getSceneHeight() { return (gamePane.getScene() != null && gamePane.getScene().getHeight() > 0) ? gamePane.getScene().getHeight() : 600; }

    private void handleNetworkMessage(Message.Parsed parsed) {
        if (parsed == null) return;
        switch (parsed.type) {
            case "JOIN": {
                String id = parsed.payload.get("id");
                if (id.equals(myId)) return;
                if (remotePlayers.containsKey(id)) return;

                String nick = parsed.payload.getOrDefault("nick", "P");
                String color = parsed.payload.getOrDefault("color", "green");
                double x = Double.parseDouble(parsed.payload.getOrDefault("x", "0"));
                double y = Double.parseDouble(parsed.payload.getOrDefault("y", "0"));

                String path = "/com/edu/example/amongus/images/" + color + ".png";
                Image img = new Image(getClass().getResourceAsStream(path));
                ImageView iv = new ImageView(img);
                iv.setFitWidth(GameConstants.PLAYER_SIZE);
                iv.setFitHeight(GameConstants.PLAYER_SIZE);

                RemotePlayer rp = new RemotePlayer(id, nick, iv, x, y);
                remotePlayers.put(id, rp);
                gamePane.getChildren().add(iv);
                break;
            }
            case "MOVE": {
                String id = parsed.payload.get("id");
                if (id.equals(myId)) break;

                double x = Double.parseDouble(parsed.payload.getOrDefault("x", "0"));
                double y = Double.parseDouble(parsed.payload.getOrDefault("y", "0"));

                RemotePlayer rp = remotePlayers.get(id);
                if (rp != null) { rp.x = x; rp.y = y; }
                else {
                    Image img = new Image(getClass().getResourceAsStream("/com/edu/example/amongus/images/green.png"));
                    ImageView iv = new ImageView(img);
                    iv.setFitWidth(GameConstants.PLAYER_SIZE);
                    iv.setFitHeight(GameConstants.PLAYER_SIZE);
                    rp = new RemotePlayer(id, "P", iv, x, y);
                    remotePlayers.put(id, rp);
                    gamePane.getChildren().add(iv);
                }
                break;
            }
            case "LEAVE": {
                String id = parsed.payload.get("id");
                RemotePlayer rp = remotePlayers.remove(id);
                if (rp != null) gamePane.getChildren().remove(rp.view);
                break;
            }
            case "CHAT": {
                String id = parsed.payload.get("id");
                String msg = parsed.payload.get("msg");
                System.out.println("CHAT from " + id + ": " + msg);
                break;
            }
        }
    }

    private static class RemotePlayer {
        String id; String nick; ImageView view; double x, y;
        RemotePlayer(String id, String nick, ImageView v, double x, double y) { this.id = id; this.nick = nick; this.view = v; this.x = x; this.y = y; }
    }
}
