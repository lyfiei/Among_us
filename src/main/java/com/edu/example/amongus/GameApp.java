package com.edu.example.amongus;

import com.edu.example.amongus.logic.GameConfig;
import com.edu.example.amongus.net.GameClient;
import com.edu.example.amongus.net.Message;
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

    private final Canvas fogCanvas; // 保留迷雾画布
    // 网络
    private GameClient client;
    private final String myId;
    private  String myNick;
    private  String myColor;

    // 远端玩家: id -> RemotePlayer （注意使用 java.util.Map，避免与 Mapp 冲突）
    private final Map<String, RemotePlayer> remotePlayers = new HashMap<>();

    public GameApp(Pane pane) {
        this.gamePane = pane;
        this.inputHandler = new InputHandler();

        // 生成 id 和昵称（后面可以替换为用户输入）
        this.myId = UUID.randomUUID().toString();
        this.myNick = GameConfig.getPlayerName();
        if (this.myNick == null || this.myNick.isEmpty()) {
            this.myNick = "P" + myId.substring(0, 4);
        }

        this.myColor = GameConfig.getPlayerColor();
        if (this.myColor == null) {
            this.myColor = "green";
        }

        try {
            Image mapImage = new Image(getClass().getResourceAsStream("/com/edu/example/amongus/images/map1.png"));
            Image collisionImage = new Image(getClass().getResourceAsStream("/com/edu/example/amongus/images/map2.jpg"));
            Image playerImage = new Image(
                    getClass().getResourceAsStream("/com/edu/example/amongus/images/" + myColor + ".png")
            );

            gameMap = new Mapp(mapImage, collisionImage);
            player = new Player(1650, 500, playerImage, gameMap.getCollisionReader());

            // 添加地图与本地玩家到 Pane（顺序决定图层）
            gamePane.getChildren().add(gameMap.getMapView());
            gamePane.getChildren().add(player.getView());

            // 迷雾画布（可选，大小用地图世界大小）
            fogCanvas = new Canvas(GameConstants.MAP_WIDTH, GameConstants.MAP_HEIGHT);
            gamePane.getChildren().add(fogCanvas);

        } catch (Exception e) {
            throw new RuntimeException("资源加载失败", e);
        }

        // 尝试连接服务器（默认本机 127.0.0.1:55555）
        try {
            this.client = new GameClient("127.0.0.1", 55555, parsed -> {
                // 网络回调在 client 线程，切回 JavaFX 线程
                Platform.runLater(() -> handleNetworkMessage(parsed));
            });

            // 发送 JOIN（带初始坐标）
            Map<String, String> payload = new HashMap<>();
            payload.put("id", myId);
            payload.put("nick", myNick);
            payload.put("color", myColor);
            payload.put("x", String.valueOf(player.getX()));
            payload.put("y", String.valueOf(player.getY()));
            client.send("JOIN", payload);

            System.out.println("Connected to server as " + myNick + " (" + myId + ")");

        } catch (IOException ex) {
            System.out.println("无法连接服务器（进入离线模式）: " + ex.getMessage());
            this.client = null; // 离线模式
        }
    }

    public void handleInput(Scene scene) {
        scene.setOnKeyPressed(e -> {
            inputHandler.press(e.getCode());
            // 示例任务按键
            if (e.getCode() == KeyCode.T) {
                // TODO: 触发任务面板
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

                if (dx != 0 && dy != 0) {
                    dx /= Math.sqrt(2);
                    dy /= Math.sqrt(2);
                }//斜向移动处理

                // 本地移动（已有碰撞检测）
                player.move(dx, dy);

                // 发送移动消息给服务器（如果联网）
                if (client != null && (dx != 0 || dy != 0)) {
                    Map<String, String> payload = new HashMap<>();
                    payload.put("id", myId);
                    payload.put("x", String.valueOf(player.getX()));
                    payload.put("y", String.valueOf(player.getY()));
                    try {
                        client.send("MOVE", payload);
                    } catch (IOException ex) {
                        System.out.println("发送 MOVE 失败: " + ex.getMessage());
                    }
                }

                // 摄像机跟随（把世界坐标转换为屏幕坐标）
                double offsetX = -player.getX() + getSceneWidth() / 2 - GameConstants.PLAYER_SIZE / 2;
                double offsetY = -player.getY() + getSceneHeight() / 2 - GameConstants.PLAYER_SIZE / 2;
                gameMap.getMapView().setX(offsetX);
                gameMap.getMapView().setY(offsetY);
                player.getView().setX(player.getX() + offsetX);
                player.getView().setY(player.getY() + offsetY);

                // 更新所有远端玩家的位置到屏幕
                for (RemotePlayer rp : remotePlayers.values()) {
                    rp.view.setX(rp.x + offsetX);
                    rp.view.setY(rp.y + offsetY);
                }

                // （可此处调用 updateFog(...) 绘制迷雾）
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

    // 处理来自网络的消息（在 JavaFX 线程中执行）
    private void handleNetworkMessage(Message.Parsed parsed) {
        if (parsed == null) return;
        switch (parsed.type) {
            case "JOIN": {
                String id = parsed.payload.get("id");
                String nick = parsed.payload.getOrDefault("nick", "P");
                String color = parsed.payload.getOrDefault("color", "green");
                double x = Double.parseDouble(parsed.payload.getOrDefault("x", "0"));
                double y = Double.parseDouble(parsed.payload.getOrDefault("y", "0"));

                if (id.equals(myId)) return; // 忽略自己的 join
                if (remotePlayers.containsKey(id)) return; // 已存在

                // 创建远端玩家视图
                String path = "/com/edu/example/amongus/images/" + color + ".png";
                Image img = new Image(getClass().getResourceAsStream(path));
                ImageView iv = new ImageView(img);
                iv.setFitWidth(GameConstants.PLAYER_SIZE);
                iv.setFitHeight(GameConstants.PLAYER_SIZE);

                RemotePlayer rp = new RemotePlayer(id, nick, iv, x, y);
                remotePlayers.put(id, rp);
                gamePane.getChildren().add(iv);
                System.out.println("Remote JOIN: " + id + " nick=" + nick);
                break;
            }
            case "MOVE": {
                String id = parsed.payload.get("id");
                if (id.equals(myId)) break; // 忽略自己的移动回显
                double x = Double.parseDouble(parsed.payload.getOrDefault("x", "0"));
                double y = Double.parseDouble(parsed.payload.getOrDefault("y", "0"));

                RemotePlayer rp = remotePlayers.get(id);
                if (rp != null) {
                    rp.x = x;
                    rp.y = y;
                } else {
                    // 如果还没 JOIN（顺序问题），先创建一个默认形象
                    String path = "/com/edu/example/amongus/images/green.png";
                    Image img = new Image(getClass().getResourceAsStream(path));
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
                if (rp != null) {
                    gamePane.getChildren().remove(rp.view);
                }
                break;
            }
            case "CHAT": {
                String id = parsed.payload.get("id");
                String msg = parsed.payload.get("msg");
                System.out.println("CHAT from " + id + ": " + msg);
                // TODO: 把消息展示到聊天 UI
                break;
            }
            default:
                // TODO: 处理 KILL / REPORT / VOTE 等其他消息
                break;
        }
    }

    // 远端玩家数据结构
    private static class RemotePlayer {
        String id;
        String nick;
        ImageView view;
        double x, y;
        RemotePlayer(String id, String nick, ImageView v, double x, double y) {
            this.id = id;
            this.nick = nick;
            this.view = v;
            this.x = x;
            this.y = y;
        }
    }
}
