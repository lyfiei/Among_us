package com.edu.example.amongus;

import com.edu.example.amongus.logic.GameConfig;
import com.edu.example.amongus.net.GameClient;
import com.edu.example.amongus.net.Message;
import com.edu.example.amongus.task.CardSwipeTask;
import com.edu.example.amongus.task.DownloadTask;
import com.edu.example.amongus.ui.ChatPane;
import com.edu.example.amongus.task.FixWiring;
import com.edu.example.amongus.task.TaskManager;
import com.edu.example.amongus.task.TriggerZone;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GameApp {
    private final Pane gamePane;
    private final Player player;
    private final Mapp gameMap;
    private final InputHandler inputHandler;
    Label posLabel = new Label();

    // 任务管理
    private final TaskManager taskManager;
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

    private GameConfig gameConfig;

    //GameApp 会在收到服务器 GAME_START 消息后显示玩家和地图
    public GameApp(Pane pane,Player myPlayer) {
        this.gamePane = pane;
        this.player = myPlayer;
        this.inputHandler = new InputHandler();
        // ✅ 新版 TaskManager 需要 Pane
        this.taskManager = new TaskManager(gamePane);



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

        // 昵称标签
        myNameTag = new Label(myNick);
        myNameTag.setStyle("-fx-text-fill: black; -fx-font-size: 14px; -fx-font-weight: bold;");

            // 初始化任务和触发区
            cardTask = new CardSwipeTask(gamePane);
            cardTask.setTaskCompleteListener(success -> System.out.println("刷卡完成: " + success));
            TriggerZone cardZone = new TriggerZone(1850, 810, 320, 300, "CardSwipe");
            taskManager.addTask(cardTask, cardZone);

            downloadTask = new DownloadTask();
            TriggerZone downloadZone = new TriggerZone(1200, 800, 80, 80, "Download");
            taskManager.addTask(downloadTask, downloadZone);

            fixWiring = new FixWiring();
            TriggerZone fixZone = new TriggerZone(1400, 700, 80, 80, "FixWiring");
            taskManager.addTask(fixWiring, fixZone);

        // 添加地图、玩家、昵称
        gamePane.getChildren().addAll(gameMap.getMapView(), player.getView(), myNameTag);

        // 迷雾画布
        fogCanvas = new Canvas(GameConstants.MAP_WIDTH, GameConstants.MAP_HEIGHT);
        gamePane.getChildren().add(fogCanvas);

        // 尝试连接服务器
        try {
            this.client = new GameClient("127.0.0.1", 55555, parsed -> Platform.runLater(() ->{
                System.out.println("runLater 执行: type=" + parsed.type);
                handleNetworkMessage(parsed);
            }));

            // 发送 JOIN 消息
            Map<String, String> payload = new HashMap<>();
            payload.put("id", myId);
            payload.put("nick", myNick);
            payload.put("color", myColor);
            payload.put("x", String.valueOf(player.getX()));
            payload.put("y", String.valueOf(player.getY()));
            client.send("JOIN", payload);

            GameConfig.setJoined(true);

            System.out.println("Connected to server as " + myNick + " (" + myId + ")");
        } catch (IOException ex) {
            System.out.println("无法连接服务器（进入离线模式）: " + ex.getMessage());
            this.client = null;
        }


        // 聊天面板
        chatPane = new ChatPane(msg -> {
            if (client != null) {
                try {
                    Map<String, String> pl = new HashMap<>();
                    pl.put("id", myId);
                    pl.put("nick", myNick);
                    pl.put("color", myColor);
                    pl.put("msg", msg);
                    // 这里发消息
                    client.send("CHAT", pl);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            // 发送完消息回到游戏焦点
            Platform.runLater(() -> gamePane.requestFocus());
        }, myNick, myColor);

// 默认隐藏聊天面板
        chatPane.setVisible(false);
        gamePane.getChildren().add(chatPane);

// 布局绑定
        chatPane.layoutXProperty().bind(gamePane.widthProperty().subtract(320));
        chatPane.setLayoutY(40);

// 聊天按钮
        Button chatBtn = new Button("聊天室");
        chatBtn.layoutXProperty().bind(gamePane.widthProperty().subtract(80));
        chatBtn.setLayoutY(8);
        chatBtn.setOnAction(e -> {
            chatPane.toggle();
            if (chatPane.isVisible()) {
                chatPane.toFront();          // 置顶
                chatPane.requestFocusInput(); // 自动聚焦输入框
            } else {
                gamePane.requestFocus();     // 回到游戏
            }
        });
        gamePane.getChildren().add(chatBtn);

// 初始获得焦点，保证可以移动
        Platform.runLater(() -> gamePane.requestFocus());

    }

//    private void connectToServer() {
//        try {
//            this.client = new GameClient("127.0.0.1", 55555,
//                    parsed -> Platform.runLater(() -> handleNetworkMessage(parsed)));
//
//            Map<String, String> payload = new HashMap<>();
//            payload.put("id", myId);
//            payload.put("nick", myNick);
//            payload.put("color", myColor);
//            payload.put("x", String.valueOf(player.getX()));
//            payload.put("y", String.valueOf(player.getY()));
//            //客户端发送join消息
//            client.send("JOIN", payload);
//
//            System.out.println("Connected to server as " + myNick + " (" + myId + ")");
//        } catch (IOException ex) {
//            System.out.println("无法连接服务器（离线模式）: " + ex.getMessage());
//            this.client = null;
//        }
//    }

    /** 玩家输入 + 游戏循环 */
    public void handleInput(Scene scene) {
        scene.setOnKeyPressed(e -> {
            inputHandler.press(e.getCode());

            // ✅ 按键只在当前触发区有效
            switch (e.getCode()) {
                case T -> taskManager.tryStartTask("CardSwipe");
                case F -> taskManager.tryStartTask("Download");
                case G -> taskManager.tryStartTask("FixWiring");
            }
            if (!chatPane.isVisible()) inputHandler.press(e.getCode());
            if (e.getCode() == KeyCode.T) { /* TODO */ }
            if (e.getCode() == KeyCode.F) new DownloadTask().start();
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

                double offsetX = -player.getX() + getSceneWidth() / 2 - GameConstants.PLAYER_SIZE / 2;
                double offsetY = -player.getY() + getSceneHeight() / 2 - GameConstants.PLAYER_SIZE / 2;

                gameMap.getMapView().setX(offsetX);
                gameMap.getMapView().setY(offsetY);
                player.getView().setX(player.getX() + offsetX);
                player.getView().setY(player.getY() + offsetY);
                myNameTag.setLayoutX(player.getX() + offsetX);
                myNameTag.setLayoutY(player.getY() + offsetY - 20);

                for (TriggerZone z : taskManager.getZones()) {
                    z.getView().setX(z.getWorldX() + offsetX);
                    z.getView().setY(z.getWorldY() + offsetY);
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

                System.out.println("Player pos: x=" + player.getX() + ", y=" + player.getY());

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
            case "GAME_START":handleGameStart(parsed); break;
            default: break;
        }
    }

    private void handleGameStart(Message.Parsed parsed) {
        gameConfig.handleServerMessage(parsed);
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

    public Pane getGamePane() {
        return gamePane;
    }
}
