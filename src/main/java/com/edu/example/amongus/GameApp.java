package com.edu.example.amongus;

import com.edu.example.amongus.logic.GameConfig;
import com.edu.example.amongus.net.GameClient;
import com.edu.example.amongus.net.Message;
import com.edu.example.amongus.net.NetTaskManager;
import com.edu.example.amongus.task.*;
import com.edu.example.amongus.ui.ChatPane;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static javafx.application.Platform.*;

public class GameApp {
    private final Pane gamePane;
    private final Player player;
    private final Mapp gameMap;
    private final InputHandler inputHandler;
    private final TaskManager taskManager;
    private final TaskStatusBar statusBar;

    Label posLabel = new Label();

    // 任务
    private CardSwipeTask cardTask;
    private DownloadTask downloadTask;
    private FixWiring fixWiring;



    private final Canvas fogCanvas;
    private GameClient client;
    private final String myId;
    private String myNick;
    private String myColor;
    private Label myNameTag;
    private final ChatPane chatPane;
    private final Map<String, RemotePlayer> remotePlayers = new HashMap<>();
    private NetTaskManager netTaskManager;


    public GameApp(Pane pane) {
        this.gamePane = pane;
        // 先初始化必须的
        this.inputHandler = new InputHandler();
        this.statusBar = new TaskStatusBar();

        this.taskManager = new TaskManager(gamePane, statusBar);

        // 生成 id 和昵称
        this.myId = UUID.randomUUID().toString();
        this.myNick = GameConfig.getPlayerName();
        if (myNick == null || myNick.isEmpty()) myNick = "P" + myId.substring(0, 4);
        this.myColor = GameConfig.getPlayerColor();
        if (myColor == null) myColor = "green";

        // 加载资源
        Image mapImage = new Image(getClass().getResourceAsStream("/com/edu/example/amongus/images/map1.png"));
        Image collisionImage = new Image(getClass().getResourceAsStream("/com/edu/example/amongus/images/map2.jpg"));
        Image playerImage = new Image(getClass().getResourceAsStream("/com/edu/example/amongus/images/" + myColor + ".png"));

        gameMap = new Mapp(mapImage, collisionImage);
        player = new Player(1650, 500, playerImage, gameMap.getCollisionReader());

        // 昵称标签
        myNameTag = new Label(myNick);
        myNameTag.setStyle("-fx-text-fill: black; -fx-font-size: 14px; -fx-font-weight: bold;");

        // 初始化任务和触发区
        netTaskManager = new NetTaskManager(taskManager, client);
        cardTask = new CardSwipeTask(gamePane,1,netTaskManager);
        TriggerZone cardZone = new TriggerZone(2050, 980, 200, 100, "CardSwipe");
        taskManager.addTask(cardTask, cardZone);

        downloadTask = new DownloadTask(2,netTaskManager);
        TriggerZone downloadZone = new TriggerZone(1850, 810, 180, 80, "Download");
        taskManager.addTask(downloadTask, downloadZone);

        fixWiring = new FixWiring(netTaskManager);
        TriggerZone fixZone = new TriggerZone(1100, 900, 300, 100, "FixWiring");
        taskManager.addTask(fixWiring, fixZone);

        // 添加地图、玩家、昵称
        gamePane.getChildren().addAll(gameMap.getMapView(), player.getView(), myNameTag);

        // 迷雾画布
        fogCanvas = new Canvas(GameConstants.MAP_WIDTH, GameConstants.MAP_HEIGHT);
        gamePane.getChildren().add(fogCanvas);

        // 尝试连接服务器
        try {
            this.client = new GameClient("127.0.0.1", 55555, parsed -> runLater(() -> handleNetworkMessage(parsed)));
            this.netTaskManager = new NetTaskManager(taskManager, client);
            // 发送 JOIN 消息
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
            this.client = null;
            this.netTaskManager = new NetTaskManager(taskManager, null);
        }
        // 注册玩家到 TaskManager
        taskManager.setPlayer(player);

        // 添加键盘处理
        Platform.runLater(() -> {
            Scene scene = gamePane.getScene();
            if (scene != null) {
                handleInput(scene);
            } else {
                System.err.println("Scene 仍为 null，键盘事件绑定失败");
            }
        });
        // 聊天面板
        chatPane = new ChatPane(msg -> {
            if (client != null) {
                try {
                    Map<String, String> pl = new HashMap<>();
                    pl.put("id", myId);
                    pl.put("nick", myNick);
                    pl.put("color", myColor);
                    pl.put("msg", msg);
                    client.send("CHAT", pl);
                } catch (IOException e) { e.printStackTrace(); }
            }
            // 发送完消息回到游戏焦点
            runLater(() -> gamePane.requestFocus());
        }, myNick, myColor);

        gamePane.getChildren().add(chatPane);
        chatPane.layoutXProperty().bind(gamePane.widthProperty().subtract(320));
        chatPane.setLayoutY(40);
        chatPane.setVisible(false);

        // 聊天按钮
        Button chatBtn = new Button("聊天室");
        chatBtn.layoutXProperty().bind(gamePane.widthProperty().subtract(80));
        chatBtn.setLayoutY(8);
        chatBtn.setOnAction(e -> {
            chatPane.toggle();
            if (chatPane.isVisible()) {
                chatPane.toFront(); // 置顶
                chatPane.requestFocusInput(); // 自动聚焦输入框
            } else {
                gamePane.requestFocus(); // 回到游戏
            }
        });

        gamePane.getChildren().add(chatBtn);

        // 添加状态栏
        gamePane.getChildren().add(statusBar);
        statusBar.setLayoutX(20);
        statusBar.setLayoutY(20);
        statusBar.toFront();

        // 初始获得焦点，保证可以移动
        runLater(() -> gamePane.requestFocus());
    }

    public void handleInput(Scene scene) {
        scene.setOnKeyPressed(e -> {
            inputHandler.press(e.getCode());

            // ✅ 按键只在当前触发区有效
            if (e.getCode() == KeyCode.F) {
                int zoneIndex = taskManager.getCurrentZoneIndex();
                if (zoneIndex >= 0) {
                    String taskName = taskManager.getZones().get(zoneIndex).getTaskName();
                    switch (taskName) {
                        case "CardSwipe" -> cardTask.start();
                        case "Download" -> downloadTask.start();
                        case "FixWiring" -> fixWiring.start();
                    }
                    netTaskManager.completeOneStep(taskName); // 再同步给服务器
                }
            }
            if (!chatPane.isVisible()) inputHandler.press(e.getCode());
        });


        scene.setOnKeyReleased(e -> {
            if (!chatPane.isVisible()) inputHandler.release(e.getCode());
        });

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
                    try { client.send("MOVE", payload); } catch (IOException ex) { ex.printStackTrace(); }
                }

                // 计算地图偏移
                double offsetX = -player.getX() + getSceneWidth()/2 - GameConstants.PLAYER_SIZE/2;
                double offsetY = -player.getY() + getSceneHeight()/2 - GameConstants.PLAYER_SIZE/2;

               // 更新玩家和地图位置
                gameMap.getMapView().setX(offsetX);
                gameMap.getMapView().setY(offsetY);
                player.getView().setX(player.getX() + offsetX);
                player.getView().setY(player.getY() + offsetY);
                myNameTag.setLayoutX(player.getX() + offsetX);
                myNameTag.setLayoutY(player.getY() + offsetY - 20);

                // 更新触发区位置
                for (TriggerZone z : taskManager.getZones()) {
                    z.updatePosition(offsetX, offsetY);
                }

                // 更新远端玩家
                for (RemotePlayer rp : remotePlayers.values()) {
                    rp.view.setX(rp.x + offsetX);
                    rp.view.setY(rp.y + offsetY);
                    rp.nameTag.setLayoutX(rp.x + offsetX);
                    rp.nameTag.setLayoutY(rp.y + offsetY - 20);
                }


                // ✅ 每帧检测玩家是否进入触发区，高亮可按任务
                taskManager.checkTasks(player);

                // 如果玩家进入了触发区，把矩形放到最上层（确保可见）
                int currentZone = taskManager.getCurrentZoneIndex();
                if (currentZone >= 0) {
                    TriggerZone z = taskManager.getZones().get(currentZone);
                    z.getView().toFront();
                }

                taskManager.updateStatusBar();

               // System.out.println("Player pos: x=" + player.getX() + ", y=" + player.getY());

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

    private void handleNetworkMessage(Message.Parsed parsed) {
        if (parsed == null) return;
        switch (parsed.type) {
            case "JOIN": handleJoin(parsed); break;
            case "MOVE": handleMove(parsed); break;
            case "LEAVE": handleLeave(parsed); break;
            case "CHAT": handleChat(parsed); break;
            case "TASK_UPDATE": {
                String taskName = parsed.payload.get("taskName");
                int completedSteps = Integer.parseInt(parsed.payload.getOrDefault(
                        "completedSteps", "0"));

                String stepsStr = parsed.payload.getOrDefault("completedSteps", "0");
                int steps = Integer.parseInt(stepsStr);
                System.out.println("[CLIENT-RECV] 服务器广播 -> " + taskName + " steps=" + steps);

                Platform.runLater(() -> netTaskManager.onNetworkUpdate(taskName, completedSteps));
                break;
            }
            case "TASK_SYNC": {
                String all = parsed.payload.get("all");
                if (all != null) {
                    String[] tasks = all.split(",");
                    for (String t : tasks) {
                        String[] kv = t.split("=");
                        if (kv.length == 2) {
                            String taskName = kv[0];
                            try {
                                int completed = Integer.parseInt(kv[1]);
                                Platform.runLater(() -> netTaskManager.onNetworkUpdate(taskName, completed));
                            } catch (NumberFormatException e) {
                                System.err.println("TASK_SYNC 数字解析错误: " + t);
                            }
                        }
                    }
                }
                break;
            }

            default: break;
        }
    }
    private void handleTaskUpdate(Message.Parsed parsed) {
        String taskName = parsed.payload.get("taskName");
        int completedSteps = Integer.parseInt(parsed.payload.getOrDefault("completedSteps", "0"));
        netTaskManager.onNetworkUpdate(taskName, completedSteps);
    }

    private void handleJoin(Message.Parsed parsed) {
        String id = parsed.payload.get("id");
        if (id.equals(myId)) return;

        String nick = parsed.payload.getOrDefault("nick", "P");
        String color = parsed.payload.getOrDefault("color", "green");
        double x = Double.parseDouble(parsed.payload.getOrDefault("x", "0"));
        double y = Double.parseDouble(parsed.payload.getOrDefault("y", "0"));

        RemotePlayer rp = remotePlayers.get(id);
        if (rp == null) {
            Image img = new Image(getClass().getResourceAsStream("/com/edu/example/amongus/images/" + color + ".png"));
            ImageView iv = new ImageView(img);
            iv.setFitWidth(GameConstants.PLAYER_SIZE);
            iv.setFitHeight(GameConstants.PLAYER_SIZE);

            rp = new RemotePlayer(id, nick, iv, x, y);
            remotePlayers.put(id, rp);
            gamePane.getChildren().addAll(iv, rp.nameTag);
        } else {
            // 如果已经存在，更新昵称和颜色
            rp.nick = nick;
            rp.nameTag.setText(nick);
            Image img = new Image(getClass().getResourceAsStream("/com/edu/example/amongus/images/" + color + ".png"));
            rp.view.setImage(img);
        }
    }

    private void handleMove(Message.Parsed parsed) {
        String id = parsed.payload.get("id");
        if (id.equals(myId)) return;

        double x = Double.parseDouble(parsed.payload.getOrDefault("x", "0"));
        double y = Double.parseDouble(parsed.payload.getOrDefault("y", "0"));

        RemotePlayer rp = remotePlayers.get(id);
        if (rp != null) {
            rp.x = x;
            rp.y = y;
        }
    }

    private void handleLeave(Message.Parsed parsed) {
        String id = parsed.payload.get("id");
        RemotePlayer rp = remotePlayers.remove(id);
        if (rp != null) gamePane.getChildren().removeAll(rp.view, rp.nameTag);
    }

    private void handleChat(Message.Parsed parsed) {
        String id = parsed.payload.get("id");
        String nick = parsed.payload.getOrDefault("nick", "P");
        String color = parsed.payload.getOrDefault("color", "green");
        String msg = parsed.payload.getOrDefault("msg", "");
        chatPane.addMessage(nick, color, msg, id.equals(myId));
    }

    private static class RemotePlayer {
        String id, nick;
        ImageView view;
        Label nameTag;
        double x, y;
        RemotePlayer(String id, String nick, ImageView v, double x, double y) {
            this.id = id; this.nick = nick; this.view = v; this.x = x; this.y = y;
            this.nameTag = new Label(nick);
            this.nameTag.setStyle("-fx-text-fill: black; -fx-font-size: 14px; -fx-font-weight: bold;");
        }
    }
}
