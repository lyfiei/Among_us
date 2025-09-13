package com.edu.example.amongus;

import com.edu.example.amongus.logic.GameConfig;
import com.edu.example.amongus.net.GameClient;
import com.edu.example.amongus.net.Message;
import com.edu.example.amongus.task.CardSwipeTask;
import com.edu.example.amongus.task.DownloadTask;
import com.edu.example.amongus.task.FixWiring;
import com.edu.example.amongus.task.TaskManager;
import com.edu.example.amongus.task.TriggerZone;
import com.edu.example.amongus.ui.ChatPane;
import com.edu.example.amongus.ui.VotePane;
import javafx.animation.AnimationTimer;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.util.Duration;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GameApp {
    private final Pane gamePane;
    private final Player player;
    private final Mapp gameMap;
    private final InputHandler inputHandler;
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

    // meeting / vote
    private boolean inMeeting = false;
    private boolean isEliminated = false; // 本地玩家是否已被淘汰（只能阻止本地输入）
    private VotePane votePane = null;
    private final Label meetingTimerLabel = new Label();
    private Timeline meetingTimer = null;
    private Button reportBtn;
    private Label eliminatedOverlay = null;

    public GameApp(Pane pane) {
        this.gamePane = pane;
        this.inputHandler = new InputHandler();
        this.taskManager = new TaskManager(gamePane);

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

        // tasks and zones
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

        // add base nodes (map -> player -> name)
        gamePane.getChildren().addAll(gameMap.getMapView(), player.getView(), myNameTag);

        // fog canvas (会覆盖普通 Node，所以聊天/投票需要 add 到 Scene root 或 toFront)
        fogCanvas = new Canvas(GameConstants.MAP_WIDTH, GameConstants.MAP_HEIGHT);
        gamePane.getChildren().add(fogCanvas);

        // try connect server
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

        // chat pane — 不放在 gamePane 的最下层，确保在 canvas 之上
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
            Platform.runLater(() -> gamePane.requestFocus());
        }, myNick, myColor);

        addNodeToTop(chatPane);
        chatPane.layoutXProperty().bind(gamePane.widthProperty().subtract(320));
        chatPane.setLayoutY(40);
        chatPane.setVisible(false);

        // report button
        reportBtn = new Button("举报/开会");
        reportBtn.layoutXProperty().bind(gamePane.widthProperty().subtract(160));
        reportBtn.setLayoutY(8);
        reportBtn.setOnAction(e -> {
            if (client != null && !inMeeting && !isEliminated) {
                try {
                    Map<String,String> pl = new HashMap<>();
                    pl.put("id", myId);
                    pl.put("discussion", "120");
                    pl.put("vote", "60");
                    client.send("REPORT", pl);
                } catch (IOException ex) { ex.printStackTrace(); }
            }
        });
        gamePane.getChildren().add(reportBtn);

        // meeting timer label
        meetingTimerLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        meetingTimerLabel.setLayoutX(12);
        meetingTimerLabel.setLayoutY(8);
        meetingTimerLabel.setVisible(false);
        gamePane.getChildren().add(meetingTimerLabel);

        Platform.runLater(() -> gamePane.requestFocus());
    }

    /** 把 node 放到 Scene root（若 root 是 Pane）以保证其置顶；否则添加到 gamePane 并 toFront() */
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

    public void handleInput(Scene scene) {
        scene.setOnKeyPressed(e -> {
            inputHandler.press(e.getCode());
            switch (e.getCode()) {
                case T -> taskManager.tryStartTask("CardSwipe");
                case F -> taskManager.tryStartTask("Download");
                case G -> taskManager.tryStartTask("FixWiring");
            }
            if (!chatPane.isVisible()) inputHandler.press(e.getCode());
            if (e.getCode() == KeyCode.F) new DownloadTask().start();
        });

        scene.setOnKeyReleased(e -> {
            if (!chatPane.isVisible()) inputHandler.release(e.getCode());
        });

        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                // --- 本地输入与移动（只限制本地玩家） ---
                double dx = 0, dy = 0;
                if (!inMeeting && !isEliminated) {
                    if (inputHandler.isPressed(KeyCode.UP)) dy -= GameConstants.MOVEMENT_SPEED;
                    if (inputHandler.isPressed(KeyCode.DOWN)) dy += GameConstants.MOVEMENT_SPEED;
                    if (inputHandler.isPressed(KeyCode.LEFT)) dx -= GameConstants.MOVEMENT_SPEED;
                    if (inputHandler.isPressed(KeyCode.RIGHT)) dx += GameConstants.MOVEMENT_SPEED;
                }
                if (dx != 0 && dy != 0) { dx /= Math.sqrt(2); dy /= Math.sqrt(2); }

                if (!inMeeting && !isEliminated) {
                    player.move(dx, dy);
                    if (client != null && (dx != 0 || dy != 0)) {
                        Map<String, String> payload = new HashMap<>();
                        payload.put("id", myId);
                        payload.put("x", String.valueOf(player.getX()));
                        payload.put("y", String.valueOf(player.getY()));
                        try { client.send("MOVE", payload); } catch (IOException ex) { ex.printStackTrace(); }
                    }
                }

                // --- 摄像机 / 渲染位置（始终运行，不受 inMeeting 影响） ---
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

                // 远程玩家渲染（始终更新视图位置以反映服务器广播的 x/y）
                for (RemotePlayer rp : remotePlayers.values()) {
                    rp.view.setX(rp.x + offsetX);
                    rp.view.setY(rp.y + offsetY);
                    rp.nameTag.setLayoutX(rp.x + offsetX);
                    rp.nameTag.setLayoutY(rp.y + offsetY - 20);
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

    /** 网络消息处理（已经在 GameClient 的回调里被 Platform.runLater 包裹） */
    private void handleNetworkMessage(Message.Parsed parsed) {
        if (parsed == null) return;
        switch (parsed.type) {
            case "JOIN": handleJoin(parsed); break;
            case "MOVE": handleMove(parsed); break;
            case "LEAVE": handleLeave(parsed); break;
            case "CHAT": handleChat(parsed); break;

            case "MEETING_DISCUSSION_START": {
                int duration = Integer.parseInt(parsed.payload.getOrDefault("duration", "120"));
                inMeeting = true;
                addNodeToTop(chatPane);
                chatPane.show();
                startMeetingCountdown(duration, "讨论剩余: %d s");
                break;
            }
            case "MEETING_VOTE_START": {
                int duration = Integer.parseInt(parsed.payload.getOrDefault("duration", "60"));
                inMeeting = true;
                addNodeToTop(chatPane);
                chatPane.show();
                showVotePane(duration);
                break;
            }
            case "VOTE_UPDATE": {
                String voter = parsed.payload.get("voter");
                String target = parsed.payload.getOrDefault("target", "");
                if (votePane != null) votePane.registerVoteUpdate(voter, target);
                break;
            }
            case "VOTE_RESULT": {
                String votedOut = parsed.payload.getOrDefault("votedOut", "");
                // 1) 更新投票面板显示（如果存在）
                if (votePane != null) votePane.showVoteResult(votedOut, myId);

                // 2) 本地处理出局：自己 or 远端玩家
                if (votedOut != null && !votedOut.isEmpty()) {
                    if (votedOut.equals(myId)) {
                        // 本地被投出：禁止本地移动并显示提示
                        isEliminated = true;
                        player.setStatus(PlayerStatus.DEAD);
                        player.getView().setVisible(false);
                        myNameTag.setVisible(false);
                        showEliminatedOverlay();
                        System.out.println("你已出局！");
                    } else {
                        RemotePlayer out = remotePlayers.get(votedOut);
                        if (out != null) {
                            out.status = PlayerStatus.DEAD;
                            out.view.setOpacity(0.4);
                            out.nameTag.setStyle("-fx-text-fill: gray; -fx-font-size: 14px; -fx-font-weight: bold;");
                        }
                    }
                }

//                // 3) 保留短暂展示后清理 UI 并恢复 inMeeting=false
//                PauseTransition delay = new PauseTransition(Duration.seconds(4));
//                delay.setOnFinished(e -> endMeetingCleanup());
//                delay.play();
//                break;
                endMeetingCleanup();
            }
            case "DEAD": {
                // 服务器确认某玩家被淘汰（双保险）
                String deadId = parsed.payload.get("id");
                if (deadId == null) break;

                if (deadId.equals(myId)) {
                    isEliminated = true;
                    player.setStatus(PlayerStatus.DEAD);
                    player.getView().setVisible(false);
                    myNameTag.setVisible(false);
                    showEliminatedOverlay();
                    System.out.println("你已被淘汰（服务器确认）！");
                } else {
                    RemotePlayer rp = remotePlayers.get(deadId);
                    if (rp != null) {
                        rp.setStatus(PlayerStatus.DEAD);
                        rp.view.setOpacity(0.4);
                        rp.nameTag.setStyle("-fx-text-fill: gray; -fx-font-size: 14px; -fx-font-weight: bold;");
                    }
                }
                // 清理会议 UI（如果存在），防止卡住 inMeeting
                endMeetingCleanup();
                break;
            }

            default: break;
        }
    }

    /** 结束会议 / 投票时的统一清理（移除投票面板、隐藏聊天室、恢复 inMeeting 标志等） */
    private void endMeetingCleanup() {
        // remove vote pane if present
        if (votePane != null) {
            if (votePane.getParent() instanceof Pane) {
                ((Pane) votePane.getParent()).getChildren().remove(votePane);
            }
            votePane = null;
        }
        // hide chat and meeting flags
        chatPane.hide();
        inMeeting = false;
        stopMeetingCountdown();
    }

    /** 创建并显示投票界面（总是重新创建新的投票面板） */
    private void showVotePane(int voteDuration) {
        // remove old if present
        if (votePane != null) {
            if (votePane.getParent() instanceof Pane) {
                ((Pane) votePane.getParent()).getChildren().remove(votePane);
            }
            votePane = null;
        }

        votePane = new VotePane(client, myId);
        votePane.addPlayer(myId, myNick, myColor, player.getStatus());
        for (RemotePlayer rp : remotePlayers.values()) {
            votePane.addPlayer(rp.id, rp.nick, rp.color, rp.status);
        }

        votePane.startCountdown(voteDuration);
        addNodeToTop(votePane);
        votePane.setLayoutX(80);
        votePane.setLayoutY(60);
        votePane.toFront();
    }

    private void startMeetingCountdown(int seconds, String labelFormat) {
        stopMeetingCountdown();
        meetingTimerLabel.setVisible(true);
        final int[] left = {seconds};
        meetingTimerLabel.setText(String.format(labelFormat, left[0]));
        meetingTimer = new Timeline(new javafx.animation.KeyFrame(Duration.seconds(1), ev -> {
            left[0]--;
            meetingTimerLabel.setText(String.format(labelFormat, left[0]));
            if (left[0] <= 0) stopMeetingCountdown();
        }));
        meetingTimer.setCycleCount(seconds);
        meetingTimer.play();
    }

    private void stopMeetingCountdown() {
        if (meetingTimer != null) { meetingTimer.stop(); meetingTimer = null; }
        meetingTimerLabel.setVisible(false);
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
            rp = new RemotePlayer(id, nick, color, iv, x, y, PlayerStatus.ALIVE);
            remotePlayers.put(id, rp);
            gamePane.getChildren().addAll(iv, rp.nameTag);
        } else {
            rp.nick = nick;
            rp.color = color;
            rp.nameTag.setText(nick);
            try {
                Image img = new Image(getClass().getResourceAsStream("/com/edu/example/amongus/images/" + color + ".png"));
                rp.view.setImage(img);
            } catch (Exception ignored) {}
        }
    }

    private void handleMove(Message.Parsed parsed) {
        String id = parsed.payload.get("id");
        if (id.equals(myId)) return; // 本地自己由本地输入发送并控制
        double x = Double.parseDouble(parsed.payload.getOrDefault("x", "0"));
        double y = Double.parseDouble(parsed.payload.getOrDefault("y", "0"));
        RemotePlayer rp = remotePlayers.get(id);
        if (rp != null) { rp.x = x; rp.y = y; } // 只更新数据，渲染在 AnimationTimer 中
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

    private void showEliminatedOverlay() {
        if (eliminatedOverlay != null) return;
        eliminatedOverlay = new Label("你已出局");
        eliminatedOverlay.setStyle("-fx-font-size: 36px; -fx-text-fill: white; -fx-background-color: rgba(0,0,0,0.7); -fx-padding:20; -fx-background-radius:8;");
        addNodeToTop(eliminatedOverlay);
        eliminatedOverlay.setLayoutX((getSceneWidth() - 300) / 2);
        eliminatedOverlay.setLayoutY((getSceneHeight() - 80) / 2);
    }

    private static class RemotePlayer {
        String id, nick;
        String color;
        ImageView view;
        Label nameTag;
        double x, y;
        PlayerStatus status = PlayerStatus.ALIVE;

        RemotePlayer(String id, String nick, String color, ImageView v, double x, double y, PlayerStatus status) {
            this.id = id;
            this.nick = nick;
            this.color = color;
            this.view = v;
            this.x = x;
            this.y = y;
            this.nameTag = new Label(nick);
            this.nameTag.setStyle("-fx-text-fill: black; -fx-font-size: 14px; -fx-font-weight: bold;");
            this.status = status;
        }

        public void setStatus(PlayerStatus playerStatus) {
            this.status = playerStatus;
            if (playerStatus == PlayerStatus.DEAD) {
                view.setOpacity(0.4); // 半透明
                nameTag.setStyle("-fx-text-fill: gray;");
            } else {
                view.setOpacity(1.0); // 恢复正常
                nameTag.setStyle("-fx-text-fill: black;");
            }
        }

        public Node getView() {
            return view;
        }
    }
}
