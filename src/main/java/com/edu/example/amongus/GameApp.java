package com.edu.example.amongus;

import com.edu.example.amongus.logic.GameConfig;
import com.edu.example.amongus.net.GameClient;
import com.edu.example.amongus.net.Message;
import com.edu.example.amongus.net.NetTaskManager;
import com.edu.example.amongus.task.*;
import com.edu.example.amongus.ui.ChatPane;
import com.edu.example.amongus.ui.PlayerActionUI;
import com.edu.example.amongus.ui.MatchUI;
import com.edu.example.amongus.ui.MatchUpdateListener;
import com.edu.example.amongus.ui.VotePane;
import com.google.gson.Gson;
import javafx.animation.AnimationTimer;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.Stage;
import javafx.util.Duration;
import com.edu.example.amongus.PlayerStatus;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;

import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.*;
import javafx.scene.shape.Rectangle;
import javafx.scene.input.KeyCode;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;


import static javafx.application.Platform.*;
import javafx.scene.input.KeyCode;

import com.edu.example.amongus.ui.StartMenuController;
import com.edu.example.amongus.ui.SettlementScreen;
public class GameApp {


    private final Pane gamePane;
    private final Player player;
    private PlayerActionUI actionUI;
    private final Mapp gameMap;
    private final InputHandler inputHandler;
    private final ReactorSabotage reactorSabotage;
    //task
    private NetTaskManager netTaskManager;
    private final TaskStatusBar statusBar;
    private final TaskManager taskManager;
    private CardSwipeTask cardTask;
    private DownloadTask downloadTask;
    private FixWiring fixWiring;

    //event
    // 类成员

    private final Canvas fogCanvas;
    private GameClient client;
    private final String myId;
    private String myNick;
    private String myColor;
    private Label myNameTag;
    private final ChatPane chatPane;
    private final Map<String, RemotePlayer> remotePlayers = new HashMap<>();

    private GameConfig gameConfig;


    //kill
    private Button killBtn;

    // meeting / vote
    private boolean inMeeting = false;
    private boolean isEliminated = false;
    private VotePane votePane = null;
    private final Label meetingTimerLabel = new Label();
    private Timeline meetingTimer = null;
    private Button reportBtn;
    private Label eliminatedOverlay = null;

    private Group worldGroup;  // 世界节点（地图、玩家、雾等）
    private Pane viewPort;     // 固定视野容器



    private MiniMap miniMap; // 小地图
    private Image goodMapBg; // 好人小地图背景
    private Image evilMapBg; // 坏人小地图背景
    private Image playerIcon; // 玩家头像

    private MatchUI matchUI;
    //匹配回调监听
    private static MatchUpdateListener matchUpdateListener;
    private StartMenuController startMenuController;

    public static void setMatchUpdateListener(MatchUpdateListener listener) {
        matchUpdateListener = listener;
    }


    public GameApp(Pane pane) {
        this.gamePane = pane;
        this.inputHandler = new InputHandler();

        //task
        this.statusBar = new TaskStatusBar();
        this.taskManager = new TaskManager(gamePane, statusBar);

        // id/nick/color
        this.myId = UUID.randomUUID().toString();
        this.myNick = GameConfig.getPlayerName();
        if (myNick == null || myNick.isEmpty()) myNick = "P" + myId.substring(0, 4);
        this.myColor = GameConfig.getPlayerColor();
        if (myColor == null) myColor = "green";

        // load resources & create player
        Image mapImage = new Image(getClass().getResourceAsStream("/com/edu/example/amongus/images/map1.png"));
        Image collisionImage = new Image(getClass().getResourceAsStream("/com/edu/example/amongus/images/map2.jpg"));
        Image playerImage = new Image(getClass().getResourceAsStream("/com/edu/example/amongus/images/" + myColor + ".png"));

        gameMap = new Mapp(mapImage, collisionImage);
        player = new Player(1650, 500, playerImage, gameMap.getCollisionReader(), PlayerStatus.ALIVE);

        myNameTag = new Label(myNick);
        myNameTag.setStyle("-fx-text-fill: black; -fx-font-size: 14px; -fx-font-weight: bold;");

        this.reactorSabotage = new ReactorSabotage(this);

        // add base nodes
        gamePane.getChildren().addAll(gameMap.getMapView(), player.getView(), myNameTag);

        // fog canvas
        fogCanvas = new Canvas(GameConstants.MAP_WIDTH, GameConstants.MAP_HEIGHT);
        gamePane.getChildren().add(fogCanvas);

        // 生成全玩家列表（本地 + 远程）
        List<Player> allPlayers = new ArrayList<>();
        allPlayers.add(player); // 本地玩家

        // 创建 PlayerActionUI
        actionUI = new PlayerActionUI(player, allPlayers, gamePane);
        this.killBtn = actionUI.getKillButton();
        Label roleLabel = actionUI.getRoleLabel();
        // try connect server
        try {
            this.client = new GameClient("192.168.43.124", 16789, parsed -> Platform.runLater(() -> handleNetworkMessage(parsed)));

            player.setName(myId);

            Map<String, String> payload = new HashMap<>();
            payload.put("id", myId);
            payload.put("nick", myNick);
            payload.put("color", myColor);
            payload.put("x", String.valueOf(player.getX()));
            payload.put("y", String.valueOf(player.getY()));
            client.send("JOIN", payload);

            GameConfig.setJoined(true);

            System.out.println("[DEBUG] Connected to server as " + myNick + " (" + myId + ")");
        } catch (IOException ex) {
            System.out.println("[DEBUG] 无法连接服务器（离线模式）: " + ex.getMessage());
            this.client = null;
            this.netTaskManager = new NetTaskManager(taskManager, null);
        }

        //task
        // 注册玩家到 TaskManager
        taskManager.setPlayer(player);
        this.taskManager.setNetTaskManager(netTaskManager);
        // tasks and zones
        netTaskManager = new NetTaskManager(taskManager, client);
        cardTask = new CardSwipeTask("CardSwipeTask",gamePane,1,netTaskManager);
        TriggerZone cardZone = new TriggerZone(2050, 980, 200, 100, "CardSwipe");
        taskManager.addTask(cardTask, cardZone);

        downloadTask = new DownloadTask( "DownloadTask",2,netTaskManager);
        TriggerZone downloadZone = new TriggerZone(1850, 810, 180, 80, "Download");
        taskManager.addTask(downloadTask, downloadZone);

        fixWiring = new FixWiring("FixWiring",netTaskManager);
        TriggerZone fixZone = new TriggerZone(1100, 900, 300, 100, "FixWiring");
        taskManager.addTask(fixWiring, fixZone);




        // chat pane
        chatPane = new ChatPane(msg -> {
            if (isEliminated) {
                System.out.println("[DEBUG] 已淘汰，无法发送消息");
                return;
            }
            if (client != null) {
                try {
                    Map<String, String> pl = new HashMap<>();
                    pl.put("id", myId);
                    pl.put("nick", myNick);
                    pl.put("color", myColor);
                    pl.put("msg", msg);
                    client.send("CHAT", pl);
                    System.out.println("[DEBUG] Sent CHAT: " + msg);
                } catch (IOException e) { e.printStackTrace(); }
            }
            // 请求回到 gamePane（放到 UI 线程）
            Platform.runLater(() -> {
                gamePane.requestFocus();
            });
        }, myNick, myColor);

        addNodeToTop(chatPane);
        chatPane.layoutXProperty().bind(gamePane.widthProperty().subtract(320));
        chatPane.setLayoutY(40);
        chatPane.setVisible(false);

        // report button
        reportBtn = new Button("举报/开会");
        reportBtn.layoutXProperty().bind(gamePane.widthProperty().subtract(130));
        // 添加这行代码来将按钮定位到底部
       reportBtn.layoutYProperty().bind(gamePane.heightProperty().subtract(reportBtn.heightProperty().add(20)));

        reportBtn.setOnAction(e -> {
            if (isEliminated) return; // 已淘汰不能举报/开会
            if (client != null && !inMeeting && !isEliminated) {
                try {
                    Map<String,String> pl = new HashMap<>();
                    pl.put("id", myId);
                    pl.put("discussion", "10");
                    pl.put("vote", "11");
                    client.send("REPORT", pl);
                    System.out.println("[DEBUG] Sent REPORT");
                } catch (IOException ex) { ex.printStackTrace(); }
            }

        });

// 鼠标按下/松开效果
        DropShadow shadow = new DropShadow(10, Color.BLACK);
        reportBtn.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            reportBtn.setScaleX(0.95);
            reportBtn.setScaleY(0.95);
            reportBtn.setEffect(shadow);
            reportBtn.setOpacity(0.8);

        });

        reportBtn.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
            reportBtn.setScaleX(1.0);
            reportBtn.setScaleY(1.0);
            reportBtn.setEffect(null);
            reportBtn.setOpacity(1.0);

        });

// 鼠标悬停效果（可选）

        reportBtn.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> reportBtn.setOpacity(0.9));
        reportBtn.addEventHandler(MouseEvent.MOUSE_EXITED, e -> reportBtn.setOpacity(1.0));

        //task: 添加状态栏
        gamePane.getChildren().add(statusBar);
        statusBar.setLayoutX(20);
        statusBar.setLayoutY(20);
        statusBar.toFront();


        // 1️⃣ 加载小地图资源
        goodMapBg = new Image(getClass().getResourceAsStream("/com/edu/example/amongus/images/minimap_good.png"));
        evilMapBg = new Image(getClass().getResourceAsStream("/com/edu/example/amongus/images/minimap_evil.png"));
        playerIcon = new Image(getClass().getResourceAsStream("/com/edu/example/amongus/images/myicon.png"));

        double scale = 0.25; // 缩放比例，比如 20% 尺寸


        miniMap = new MiniMap(
                Player.PlayerType.EVIL.equals(player.getType()) ? evilMapBg : goodMapBg,
                playerIcon,
                GameConstants.MAP_WIDTH,   // 游戏地图宽
                GameConstants.MAP_HEIGHT,  // 游戏地图高
                GameConstants.MAP_WIDTH * scale,   // 显示宽
                GameConstants.MAP_HEIGHT * scale   // 显示高
        );
        miniMap.setVisible(false);
        gamePane.getChildren().add(miniMap);

// ✅ 居中显示
        miniMap.layoutXProperty().bind(gamePane.widthProperty().subtract(miniMap.getPrefWidth()).divide(2));
        miniMap.layoutYProperty().bind(gamePane.heightProperty().subtract(miniMap.getPrefHeight()).divide(2));


        Image miniMapImageBtn = new Image(getClass().getResourceAsStream("/com/edu/example/amongus/images/minimap_btn.png"));
        ImageView miniMapBtn = new ImageView(miniMapImageBtn);

// 设置按钮大小
        miniMapBtn.setFitWidth(80);
        miniMapBtn.setFitHeight(80);

// 将按钮放置在右上角，距离边缘 8 像素
        miniMapBtn.setLayoutY(8);
        miniMapBtn.layoutXProperty().bind(
                gamePane.widthProperty().subtract(miniMapBtn.getFitWidth()).subtract(8)
        );

// 将按钮添加到游戏界面中
        gamePane.getChildren().addAll(miniMapBtn,reportBtn);
// 点击事件：显示/隐藏小地图，并更新玩家位置
        miniMapBtn.setOnMouseClicked(e -> {
            boolean visible = !miniMap.isVisible();
            miniMap.setVisible(visible);

            if (visible) {
                miniMap.toFront(); // 先将 miniMap 置顶
                miniMapBtn.toFront(); // 再将按钮置顶，确保它在 miniMap 之上
                miniMap.updatePlayerPosition(player.getX(), player.getY()); // 更新玩家位置
            } else {
                gamePane.requestFocus(); // 回到游戏焦点
            }
        });

// 添加按下/松开效果（可选，更像按钮）

        miniMapBtn.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            miniMapBtn.setScaleX(0.95);
            miniMapBtn.setScaleY(0.95);
            miniMapBtn.setEffect(shadow);
            miniMapBtn.setOpacity(0.8);
        });

        miniMapBtn.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
            miniMapBtn.setScaleX(1.0);
            miniMapBtn.setScaleY(1.0);
            miniMapBtn.setEffect(null);
            miniMapBtn.setOpacity(1.0);
        });

        miniMapBtn.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> miniMapBtn.setOpacity(0.9));
        miniMapBtn.addEventHandler(MouseEvent.MOUSE_EXITED, e -> miniMapBtn.setOpacity(1.0));


        allPlayers.add(player); // 本地玩家
        // RemotePlayer 目前是 RemotePlayer 类型，不能直接加入 Player
        // 如果以后需要同步 kill，可能要做 RemotePlayer -> Player 的代理或封装

        // 创建 PlayerActionUI
        PlayerActionUI actionUI = new PlayerActionUI(player, allPlayers, gamePane);
        Button killBtn = actionUI.getKillButton();
        if (!gamePane.getChildren().contains(killBtn)) {
            gamePane.getChildren().add(killBtn);
        }

        killBtn.setVisible(true); // 强制显示
        // 右下角自适应
        killBtn.layoutXProperty().bind(gamePane.widthProperty().subtract(140)); // 假设按钮宽 120
        killBtn.layoutYProperty().bind(gamePane.heightProperty().subtract(60));

        // 放到最上层
        Platform.runLater(() -> killBtn.toFront());


        // meeting timer label
        meetingTimerLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        meetingTimerLabel.setLayoutX(12);
        meetingTimerLabel.setLayoutY(8);
        meetingTimerLabel.setVisible(false);
        gamePane.getChildren().add(meetingTimerLabel);

        // make gamePane focus traversable so it can receive key events
        gamePane.setFocusTraversable(true);
        // initial focus request (in case Scene is ready)
        Platform.runLater(() -> gamePane.requestFocus());
    }

    //kill
    public RemotePlayer getRemotePlayerById(String id) {
        return id == null ? null : remotePlayers.get(id);
    }

    private void addNodeToTop(Node node) {
        if (gamePane.getScene() != null && gamePane.getScene().getRoot() instanceof Pane) {
            Pane root = (Pane) gamePane.getScene().getRoot();
            if (!root.getChildren().contains(node)) root.getChildren().add(node);
            node.toFront();
        } else {
            if (!gamePane.getChildren().contains(node)) gamePane.getChildren().add(node);
            node.toFront();
        }
    }

    /**
     * 处理输入 — 必须在创建 Scene 并显示/加载后调用： gameApp.handleInput(scene);
     */
    public void handleInput(Scene scene) {
        // Safety: make root and gamePane focusable and request focus
        if (scene.getRoot() != null) {
            scene.getRoot().setFocusTraversable(true);
            Platform.runLater(() -> {
                try {
                    scene.getRoot().requestFocus();
                } catch (Exception ignored) {}
            });
        }
        gamePane.setFocusTraversable(true);

        // Use event filters so keystrokes are captured even if focus briefly on controls
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                reactorSabotage.handleKeyPress(e.getCode());
            // Debug log for key press
            System.out.println("[DEBUG] Key pressed: " + e.getCode() + " (chat visible=" + chatPane.isVisible() + ")");
            // If chat visible, don't add to movement keys
            if (!chatPane.isVisible()) {
                inputHandler.press(e.getCode());
            }
            // hotkeys for tasks (allow even if chat visible? keep original behavior: no)
            if (e.getCode() == KeyCode.F) {
                int zoneIndex = taskManager.getCurrentZoneIndex();
                if (zoneIndex >= 0) {
                    String taskName = taskManager.getZones().get(zoneIndex).getTaskName();

                    // 保留原有任务启动逻辑
                    switch (taskName) {
                        case "CardSwipe" -> cardTask.start();
                        case "Download" -> downloadTask.start();
                        case "FixWiring" -> fixWiring.start();
                    }

                    // 增强网络同步（添加状态检查和错误处理）
                    try {
                        if (netTaskManager != null) {
                            System.out.println("[DEBUG] 同步任务进度: " + taskName); // 调试日志
                            netTaskManager.completeOneStep(taskName);
                        } else {
                            System.out.println("[WARN] netTaskManager未初始化");
                        }
                    } catch (Exception ex) {
                        System.err.println("[ERROR] 同步任务失败: " + ex.getMessage());
                    }
                }
            }

        });

        killBtn.setOnAction(ev -> {
            System.out.println("[DEBUG] 点击杀人按钮");
            System.out.println("[DEBUG] 本地玩家类型: " + player.getType());
            System.out.println("[DEBUG] 远程玩家数量: " + remotePlayers.size());

            // 打印所有远程玩家信息
            for (RemotePlayer rp : remotePlayers.values()) {
                System.out.println("[DEBUG] 远程玩家 " + rp.id + ": 状态=" + rp.getStatus() +
                        ", 类型=" + rp.getType() +
                        ", 位置=(" + rp.getX() + "," + rp.getY() + ")");
            }
            // 先本地检查身份 & 冷却
            if (player.getType() != com.edu.example.amongus.Player.PlayerType.EVIL) {
                System.out.println("[DEBUG] 你不是坏人，不能杀人");
                return;
            }
            long remaining = player.getKillCooldownRemaining();
            if (remaining > 0) {
                System.out.println("[DEBUG] 冷却中，还剩 " + (remaining/1000) + "s");
                return;
            }
            // 找最近的远程目标（也可以扩展为包含本地 other players）
            RemotePlayer victim = findNearestRemoteWithinRange();
            if (victim == null) {
                System.out.println("[DEBUG] 附近没有可杀的玩家");
                return;
            }

            // 发送网络请求给服务器（类型名统一用 "KILL"）
            if (client != null) {
                Map<String, String> payload = new HashMap<>();
                payload.put("killer", myId);
                payload.put("victim", victim.id);
                try {
                    client.send("KILL", payload);
                    // 立刻进入冷却，避免多次点击（服务器确认之后我们会收到 DEAD）
                    player.markKillUsed();
                    System.out.println("[DEBUG] 发送 KILL -> victim=" + victim.id);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            } else {
                // 离线模式：直接本地处理（仅用于测试）
                victim.setStatus(PlayerStatus.DEAD);
                player.markKillUsed();
                System.out.println("[DEBUG] 离线模式直接处理击杀: " + victim.id);
            }
            killBtn.setVisible(player.getType() == Player.PlayerType.EVIL);
            killBtn.setDisable(false);
        });


        scene.addEventFilter(KeyEvent.KEY_RELEASED, e -> {
            // Debug log for key release
            System.out.println("[DEBUG] Key released: " + e.getCode() + " (chat visible=" + chatPane.isVisible() + ")");
            if (!chatPane.isVisible()) {
                inputHandler.release(e.getCode());
            }
        });

        // click to focus back to gamePane
        scene.setOnMouseClicked(e -> {
            System.out.println("[DEBUG] Scene clicked, requesting focus to gamePane");
            gamePane.requestFocus();
        });

        // Animation / game loop
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                double dx = 0, dy = 0;
                if (!inMeeting && !isEliminated) {
                    if (inputHandler.isPressed(KeyCode.UP) || inputHandler.isPressed(KeyCode.W)) dy -= GameConstants.MOVEMENT_SPEED;
                    if (inputHandler.isPressed(KeyCode.DOWN) || inputHandler.isPressed(KeyCode.S)) dy += GameConstants.MOVEMENT_SPEED;
                    if (inputHandler.isPressed(KeyCode.LEFT) || inputHandler.isPressed(KeyCode.A)) dx -= GameConstants.MOVEMENT_SPEED;
                    if (inputHandler.isPressed(KeyCode.RIGHT) || inputHandler.isPressed(KeyCode.D)) dx += GameConstants.MOVEMENT_SPEED;
                }

                if (dx != 0 && dy != 0) { dx /= Math.sqrt(2); dy /= Math.sqrt(2); }


                if (!inMeeting && !isEliminated) {
                    // Try to move local player
                    player.move(dx, dy);

                    // If moved, send server update
                    if (client != null && (dx != 0 || dy != 0)) {
                        Map<String, String> payload = new HashMap<>();
                        payload.put("id", myId);
                        payload.put("x", String.valueOf(player.getX()));
                        payload.put("y", String.valueOf(player.getY()));
                        try {
                            client.send("MOVE", payload);
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    }
                }

                // camera / rendering
                double offsetX = -player.getX() + getSceneWidth() / 2 - GameConstants.PLAYER_SIZE / 2;
                double offsetY = -player.getY() + getSceneHeight() / 2 - GameConstants.PLAYER_SIZE / 2;

                gameMap.getMapView().setX(offsetX);
                gameMap.getMapView().setY(offsetY);
                player.getView().setX(player.getX() + offsetX);
                player.getView().setY(player.getY() + offsetY);
                myNameTag.setLayoutX(player.getX() + offsetX);
                myNameTag.setLayoutY(player.getY() + offsetY - 20);

                // 更新task触发区位置
                for (TriggerZone z : taskManager.getZones()) {
                    z.updatePosition(offsetX, offsetY);
                }

                for (RemotePlayer rp : remotePlayers.values()) {
                    rp.view.setX(rp.x + offsetX);
                    rp.view.setY(rp.y + offsetY);
                    rp.nameTag.setLayoutX(rp.x + offsetX);
                    rp.nameTag.setLayoutY(rp.y + offsetY - 20);
                }

                // ✅ task:每帧检测玩家是否进入触发区，高亮可按任务
                taskManager.checkTasks(player);

                // task:如果玩家进入了触发区，把矩形放到最上层（确保可见）
                int currentZone = taskManager.getCurrentZoneIndex();
                if (currentZone >= 0) {
                    TriggerZone z = taskManager.getZones().get(currentZone);
                    z.getView().toFront();
                }

                taskManager.checkTasks(player);
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

    // --- 网络消息处理 (handleNetworkMessage) ---
    private void handleNetworkMessage(Message.Parsed parsed) {
        if (parsed == null) return;

        switch (parsed.type) {
            case "JOIN": handleJoin(parsed); break;
            case "MOVE": handleMove(parsed); break;
            case "LEAVE": handleLeave(parsed); break;
            case "CHAT": handleChat(parsed); break;
            case "GAME_START": handleGameStart(parsed); break;
            case "ROLE": {
                String role = parsed.payload.get("type"); // 服务器分配的角色
                String targetId = parsed.payload.get("target"); // 玩家ID

                if (role == null || targetId == null) {
                    System.out.println("[WARN] 收到无效的ROLE消息，缺少type或target字段");
                    break;
                }
                Player.PlayerType playerType = role.equalsIgnoreCase("EVIL") ?
                        Player.PlayerType.EVIL : Player.PlayerType.GOOD;

                if (targetId.equals(myId)) {
                    // 更新本地玩家角色
                    player.setType(playerType);
                    System.out.println("[DEBUG] 你被分配为: " + player.getType());

                    // 更新 UI
                    if (actionUI != null) {
                        Platform.runLater(actionUI::updatePlayerType);
                    }
                } else {
                    // 更新远程玩家角色
                    RemotePlayer rp = remotePlayers.get(targetId);
                    if (rp != null) {
//                        rp.type = role.equalsIgnoreCase("EVIL") ? RemotePlayer.PlayerType.EVIL : RemotePlayer.PlayerType.GOOD;
                        rp.type = playerType;
                        System.out.printf("[DEBUG] 设置玩家 %s 角色为: %s\n", rp.nick, rp.type);
                    } else {
                        System.out.printf("[WARN] 找不到远程玩家 %s 来设置角色\n", targetId);
                    }
                }
                break;
            }

            case "MATCH_UPDATE": {
                String curStr = parsed.payload.get("current");
                String totalStr = parsed.payload.get("total");

                if (curStr != null && totalStr != null) {
                   int current = Integer.parseInt(curStr);
                    System.out.println("current: " + current);
                   int total = Integer.parseInt(totalStr);
                    if (matchUpdateListener != null) {
                        Platform.runLater(() -> matchUpdateListener.onMatchUpdate(current, total));
                    }
                }
                break;
            }
            case "MEETING_DISCUSSION_START": {
                int duration = Integer.parseInt(parsed.payload.getOrDefault("duration", "120"));
                inMeeting = true; // 开始讨论

                    try {
                        String videoPath = getClass().getResource("/com/edu/example/amongus/videos/meeting.mp4").toExternalForm();
                        Media media = new Media(videoPath);
                        MediaPlayer mediaPlayer = new MediaPlayer(media);
                        MediaView mediaView = new MediaView(mediaPlayer);
                        mediaView.setPreserveRatio(false);
                        mediaView.fitWidthProperty().bind(gamePane.widthProperty());
                        mediaView.fitHeightProperty().bind(gamePane.heightProperty());

// 添加到 gamePane
                        gamePane.getChildren().add(mediaView);

// 播放结束后移除 MediaView
                        mediaPlayer.setOnEndOfMedia(() -> {
                            gamePane.getChildren().remove(mediaView);
                            addNodeToTop(chatPane);
                            chatPane.show();
                            startMeetingCountdown(duration, "讨论剩余: %d s");
                        });

                        mediaPlayer.play();
                    } catch (Exception e) {
                        e.printStackTrace();
                        // 出错直接显示聊天页面
                        addNodeToTop(chatPane);
                        chatPane.show();
                        startMeetingCountdown(duration, "讨论剩余: %d s");
                    }

                    break;
            }
            case "MEETING_VOTE_START": {
                int duration = Integer.parseInt(parsed.payload.getOrDefault("duration","60"));
                inMeeting = true; // 开始投票
                addNodeToTop(chatPane);
                chatPane.show();
                showVotePane(duration);
                break;
            }
            case "MEETING_END": {
                inMeeting = false;

                // 移除投票面板
                if (votePane != null) {
                    if (votePane.getParent() instanceof Pane parent) {
                        parent.getChildren().remove(votePane);
                    }
                    votePane = null;
                }

                // 移除聊天室
                if (chatPane != null) {
                    if (chatPane.getParent() instanceof Pane parent) {
                        parent.getChildren().remove(chatPane);
                    }
                    chatPane.setVisible(false);
                    chatPane.setManaged(false); // ✅ 确保不挡住
                }

                System.out.println("[DEBUG] Meeting ended, UI closed.");
                System.out.println("[DEBUG] chatPane parent = " + chatPane.getParent());

                break;
            }

            case "VOTE_RESULT": {
                String votedOut = parsed.payload.getOrDefault("votedOut","");
                if (votePane != null) votePane.showVoteResult(votedOut);

                if (votedOut != null && !votedOut.isEmpty() && votedOut.equals(myId)) {
                    isEliminated = true;
                    player.setStatus(PlayerStatus.DEAD);
                    player.getView().setVisible(false);
                    myNameTag.setVisible(false);
                    showEliminatedOverlay();
                } else if (!votedOut.equals(myId)) {
                    RemotePlayer out = remotePlayers.get(votedOut);
                    if (out != null) {
                        out.status = PlayerStatus.DEAD;
                        out.view.setOpacity(0.4);
                        out.nameTag.setStyle("-fx-text-fill: gray; -fx-font-size: 14px; -fx-font-weight: bold;");
                    }
                }


                // 保留投票面板几秒，让玩家看到出局
                PauseTransition delay = new PauseTransition(Duration.seconds(4)); // 4秒停留
                delay.setOnFinished(ev -> {
                    // 统一清理（也会设置 inMeeting=false、停止计时器等）
                    endMeetingCleanup();
                });
                delay.play();

                break;
            }
            case "VOTE_UPDATE": {
                String voter = parsed.payload.get("voter");
                String target = parsed.payload.get("target");
                if (votePane != null) votePane.registerVoteUpdate(voter, target);
                break;
            }

            case "DEAD": {
                String deadId = parsed.payload.get("id");
                if (deadId == null) break;
                if(deadId.equals(myId)){
                    isEliminated = true;
                    player.setStatus(PlayerStatus.DEAD);
                    player.getView().setVisible(false);
                    myNameTag.setVisible(false);
                    showEliminatedOverlay();
                    System.out.println("[DEBUG] 你已被淘汰（服务器确认）！");
                } else {
                    RemotePlayer rp = remotePlayers.get(deadId);
                    if(rp != null) rp.setStatus(PlayerStatus.DEAD);
                }

                // 不要修改 inMeeting
                break;
            }
            //task:
            case "TASK_UPDATE": {
                String taskName = parsed.payload.get("taskName");
                int completedSteps = Integer.parseInt(parsed.payload.getOrDefault("completedSteps", "0"));

                // 更新本地任务状态
                Platform.runLater(() -> {
                    netTaskManager.onNetworkUpdate(taskName, completedSteps);

                    // 更新状态栏显示
                    TaskStatus status = taskManager.getStatusBar().getStatusByName(taskName);
                    if (status != null) {
                        status.setCompleted(completedSteps);
                        taskManager.getStatusBar().updateTask(status);
                    }
                });
                break;
            }
            case "SABOTAGE":
            case "REACTOR_FIX":
            case "SABOTAGE_RESULT":
                reactorSabotage.handleNetworkMessage(parsed);
                break;
            case "GAME_OVER": {
                String msg = parsed.payload.get("message");
                String evilJson = parsed.payload.get("evilPlayers");

                Gson gson = new Gson();
                List<Map<String, String>> evilList = gson.fromJson(evilJson, List.class);

                Platform.runLater(() -> {
                            // 先停止所有可能的音频播放，防止重叠
                            if (startMenuController != null) {
                                System.out.println("startMenuController  is not null");
                                startMenuController.stopVideo();
                            }

                            // 根据消息判断是好人胜利还是失败
                            boolean isVictory = msg.contains("好人"); // 假设消息里包含“好人”就是胜利

                            // 选择正确的音频文件路径
                            String audioPath;
                            if (isVictory) {
                                audioPath = getClass().getResource("/com/edu/example/amongus/audio/Crewmate_victory_music.mp3").toExternalForm();
                            } else {
                                audioPath = getClass().getResource("/com/edu/example/amongus/audio/Impostor_victory.mp3").toExternalForm();
                            }

                            // 创建 Media 和 MediaPlayer
                            Media sound = new Media(audioPath);
                            MediaPlayer mediaPlayer = new MediaPlayer(sound);

                            // 设置为无限循环播放
                            mediaPlayer.setCycleCount(MediaPlayer.INDEFINITE);

                            // 播放音频
                            mediaPlayer.play();

                            //此处记得要改结束音频

//                            // 将播放器实例保存到成员变量中，以便之后可以停止它
//                            if (isVictory) {
//                                victorySoundPlayer = mediaPlayer;
//                            } else {
//                                defeatSoundPlayer = mediaPlayer;
//                            }
                });

                Platform.runLater(() -> {
                    // 创建新的结算画面实例
                    SettlementScreen settlementScreen = new SettlementScreen(msg, evilList);

                    // 获取当前窗口并切换到新的场景（Scene）
                    // 假设你有一个方法可以获取当前的 Stage
                    Stage primaryStage = (Stage)(Stage) gamePane.getScene().getWindow();

                    // 创建一个新的 Scene，并把结算画面作为根节点
                    Scene settlementScene = new Scene(settlementScreen, 800, 600); // 设置你需要的窗口大小
                    primaryStage.setScene(settlementScene);
                    primaryStage.setTitle("游戏结算");
                });
                break;
            }


            default: break;
        }
    }

    private void endMeetingCleanup() {
        // 移除投票面板
        if (votePane != null) {
            try {
                votePane.stopCountdown(); // 需在 VotePane 里实现
            } catch (Exception ignored) {}
            if (votePane.getParent() instanceof Pane parent) {
                parent.getChildren().remove(votePane);
            }
            votePane = null;
        }

        // 移除聊天面板
        if (chatPane != null) {
            if (chatPane.getParent() instanceof Pane parent) {
                parent.getChildren().remove(chatPane);
            }
            chatPane.setVisible(false);
        }

        inMeeting = false;

        stopMeetingCountdown(); // 停止顶部倒计时

        // 会议结束后，把焦点交回游戏
        Platform.runLater(() -> {
            try { gamePane.requestFocus(); } catch (Exception ignored) {}
        });

        System.out.println("[DEBUG] endMeetingCleanup 完成 (inMeeting=false)");
    }



    private void showVotePane(int voteDuration) {
        if (votePane != null && votePane.getParent() instanceof Pane) {
            ((Pane) votePane.getParent()).getChildren().remove(votePane);
        }
        votePane = new VotePane(client,myId);
        votePane.addPlayer(myId,myNick,myColor,player.getStatus());
        for (RemotePlayer rp : remotePlayers.values()) {
            votePane.addPlayer(rp.id,rp.nick,rp.color,rp.status);
        }
        votePane.startCountdown(voteDuration);
        addNodeToTop(votePane);
        votePane.setLayoutX(80);
        votePane.setLayoutY(60);
        votePane.toFront();
    }

    private void startMeetingCountdown(int seconds,String labelFormat) {
        stopMeetingCountdown();
        meetingTimerLabel.setVisible(true);
        final int[] left = {seconds};
        meetingTimerLabel.setText(String.format(labelFormat,left[0]));
        meetingTimer = new Timeline(new javafx.animation.KeyFrame(Duration.seconds(1), ev -> {
            left[0]--;
            meetingTimerLabel.setText(String.format(labelFormat,left[0]));
            if(left[0]<=0) stopMeetingCountdown();
        }));
        meetingTimer.setCycleCount(seconds);
        meetingTimer.play();
    }

    private void stopMeetingCountdown() {
        if(meetingTimer != null){ meetingTimer.stop(); meetingTimer=null; }
        meetingTimerLabel.setVisible(false);
    }

    private void handleGameStart(Message.Parsed parsed) {
        gameConfig.handleServerMessage(parsed);
    }
    private void handleJoin(Message.Parsed parsed){
        String id = parsed.payload.get("id");
        if(id.equals(myId)) return;
        String nick = parsed.payload.getOrDefault("nick","P");
        String color = parsed.payload.getOrDefault("color","green");
        double x = Double.parseDouble(parsed.payload.getOrDefault("x","0"));
        double y = Double.parseDouble(parsed.payload.getOrDefault("y","0"));

        System.out.println("[DEBUG] handleJoin: id=" + id + " nick=" + nick + " at (" + x + "," + y + ")");

        RemotePlayer rp = remotePlayers.get(id);
        if(rp==null){
            Image img = new Image(getClass().getResourceAsStream("/com/edu/example/amongus/images/"+color+".png"));
            ImageView iv = new ImageView(img);
            iv.setFitWidth(GameConstants.PLAYER_SIZE);
            iv.setFitHeight(GameConstants.PLAYER_SIZE);
            rp = new RemotePlayer(id,nick,color,iv,x,y,PlayerStatus.ALIVE, Player.PlayerType.GOOD);
            remotePlayers.put(id,rp);
            gamePane.getChildren().addAll(iv,rp.nameTag);
        } else {
            rp.nick = nick; rp.color=color; rp.nameTag.setText(nick);
            try { rp.view.setImage(new Image(getClass().getResourceAsStream("/com/edu/example/amongus/images/"+color+".png"))); }
            catch(Exception ignored){}
            rp.updatePosition(x, y);
        }
    }

    private void handleMove(Message.Parsed parsed){
        String id = parsed.payload.get("id");
        if(id.equals(myId)) return;
        double x = Double.parseDouble(parsed.payload.getOrDefault("x","0"));
        double y = Double.parseDouble(parsed.payload.getOrDefault("y","0"));
        System.out.printf("[DEBUG] 玩家 %s 移动到 (%.1f,%.1f)\n", id, x, y);
        RemotePlayer rp = remotePlayers.get(id);
        if(rp!=null){  rp.updatePosition(x, y);;
        }
        System.out.println("[DEBUG] handleMove: " + id + " -> (" + x + "," + y + ")");
    }

    private void handleLeave(Message.Parsed parsed){
        String id = parsed.payload.get("id");
        RemotePlayer rp = remotePlayers.remove(id);
        if(rp!=null){
            gamePane.getChildren().removeAll(rp.view,rp.nameTag);
            System.out.println("[DEBUG] Player left: "+id);
        }
    }

    private void handleChat(Message.Parsed parsed){
        String id = parsed.payload.get("id");
        String nick = parsed.payload.getOrDefault("nick","P");
        String color = parsed.payload.getOrDefault("color","green");
        String msg = parsed.payload.getOrDefault("msg","");
        chatPane.addMessage(nick,color,msg,id.equals(myId));
        System.out.println("[DEBUG] Chat from "+nick+": "+msg);
    }

    private void showEliminatedOverlay(){
        if(eliminatedOverlay!=null) return;
        eliminatedOverlay = new Label("你已出局");
        eliminatedOverlay.setStyle("-fx-font-size: 36px; -fx-text-fill: white; -fx-background-color: rgba(0,0,0,0.7); -fx-padding:20; -fx-background-radius:8;");
        addNodeToTop(eliminatedOverlay);
        eliminatedOverlay.setLayoutX((getSceneWidth()-300)/2);
        eliminatedOverlay.setLayoutY((getSceneHeight()-80)/2);
    }

    public void setStartMenuController(StartMenuController startMenuController) {
        this.startMenuController = startMenuController;
    }

    private static class RemotePlayer {
        String id, nick, color;
        ImageView view;
        Label nameTag;
        double x, y;
        PlayerStatus status = PlayerStatus.ALIVE;

        Player.PlayerType type; // 好人 / 坏人

        RemotePlayer(String id, String nick, String color, ImageView v, double x, double y,
                     PlayerStatus status, Player.PlayerType type) {
            this.id = id;
            this.nick = nick;
            this.color = color;
            this.view = v;
            this.x = x;
            this.y = y;
            this.nameTag = new Label(nick);
            this.nameTag.setStyle("-fx-text-fill: black; -fx-font-size: 14px; -fx-font-weight: bold;");
            this.status = status;
            this.type = type; // 存储身份
        }

        public void setStatus(PlayerStatus playerStatus) {
            this.status = playerStatus;
            if (playerStatus == PlayerStatus.DEAD) {
                view.setOpacity(0.4);
                nameTag.setStyle("-fx-text-fill: gray;");
            } else {
                view.setOpacity(1.0);
                nameTag.setStyle("-fx-text-fill: black;");
            }
        }

        public double getX() { return x; }
        public double getY() { return y; }
        public PlayerStatus getStatus() { return status; }
        public Player.PlayerType getType() { return type; }
        private void updateView() {
            Platform.runLater(() -> {
                view.setX(x);
                view.setY(y);
                if (nameTag != null) {
                    nameTag.setLayoutX(x);
                    nameTag.setLayoutY(y - 20);
                }
            });
        }
        public void updatePosition(double newX, double newY) {
            this.x = newX;
            this.y = newY;
            updateView(); // 同步 ImageView + nameTag
        }
        public Node getView() {
            return view;
        }
        public String getNick() {
            return nick;
        }
    }

    private RemotePlayer findNearestRemoteWithinRange() {
        double range = Player.getKillRange(); // 使用 Player 中的常量，保证一致
        RemotePlayer nearest = null;
        double nearestDist = Double.MAX_VALUE;

        double px = player.getX() + GameConstants.PLAYER_SIZE / 2.0;
        double py = player.getY() + GameConstants.PLAYER_SIZE / 2.0;

        System.out.println("[DEBUG] 本地 player center=(" + px + "," + py + "), remote count=" + remotePlayers.size());

        for (RemotePlayer rp : remotePlayers.values()) {
            if (rp == null) continue;
            if (rp.getStatus() == PlayerStatus.DEAD) {
                System.out.println("[DEBUG] skip dead: " + rp.id);
                continue;
            }
            if (rp.getType() != Player.PlayerType.GOOD) {
                System.out.println("[DEBUG] skip non-good player: " + rp.id);
                continue;
            }
            if (rp.id.equals(myId)) continue; // 保险起见
            double rx = rp.getX() + GameConstants.PLAYER_SIZE / 2.0;
            double ry = rp.getY() + GameConstants.PLAYER_SIZE / 2.0;
            double dist = Math.hypot(px - rx, py - ry);

            System.out.printf("[DEBUG] consider %s center=(%.1f,%.1f) dist=%.1f\n", rp.id, rx, ry, dist);

            if (dist <= range && dist < nearestDist) {
                nearest = rp;
                nearestDist = dist;
            }
        }

        System.out.println("[DEBUG] nearestCandidate=" + (nearest==null ? "null" : nearest.id) + ", dist=" + nearestDist + ", usedRange=" + range);
        return nearest;
    }


    public Pane getGamePane() {
        return gamePane;
    }

    public GameClient getClient() {
        return this.client; // client 是你在 GameApp 里已有的字段
    }

    public Player getPlayer() {
        return this.player;
    }
}
