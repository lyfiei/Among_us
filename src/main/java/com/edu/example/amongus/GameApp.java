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
import com.edu.example.amongus.ui.MatchUI;
import com.edu.example.amongus.ui.MatchUpdateListener;
import com.edu.example.amongus.ui.VotePane;
import javafx.animation.AnimationTimer;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.util.Duration;
import com.edu.example.amongus.PlayerStatus;

import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static javafx.application.Platform.*;

public class GameApp {

    private GameManager gameManager;

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

    private GameConfig gameConfig;

    // meeting / vote
    private boolean inMeeting = false;
    private boolean isEliminated = false;
    private VotePane votePane = null;
    private final Label meetingTimerLabel = new Label();
    private Timeline meetingTimer = null;
    private Button reportBtn;
    private Label eliminatedOverlay = null;

    //private MiniMap miniMap; // 小地图
    private Image goodMapBg; // 好人小地图背景
    private Image evilMapBg; // 坏人小地图背景
    private Image playerIcon; // 玩家头像

    private MatchUI matchUI;
    //匹配回调监听
    private static MatchUpdateListener matchUpdateListener;
    public static void setMatchUpdateListener(MatchUpdateListener listener) {
        matchUpdateListener = listener;
    }


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
        cardTask.setTaskCompleteListener(success -> System.out.println("[DEBUG] 刷卡完成: " + success));
        TriggerZone cardZone = new TriggerZone(1850, 810, 320, 300, "CardSwipe");
        taskManager.addTask(cardTask, cardZone);

        downloadTask = new DownloadTask();
        TriggerZone downloadZone = new TriggerZone(1200, 800, 80, 80, "Download");
        taskManager.addTask(downloadTask, downloadZone);

        fixWiring = new FixWiring();
        TriggerZone fixZone = new TriggerZone(1400, 700, 80, 80, "FixWiring");
        taskManager.addTask(fixWiring, fixZone);

        // add base nodes
        gamePane.getChildren().addAll(gameMap.getMapView(), player.getView(), myNameTag);

        // fog canvas
        fogCanvas = new Canvas(GameConstants.MAP_WIDTH, GameConstants.MAP_HEIGHT);
        gamePane.getChildren().add(fogCanvas);

        // try connect server
        try {
            this.client = new GameClient("127.0.0.1", 33333, parsed -> Platform.runLater(() -> handleNetworkMessage(parsed)));

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
        }

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
        reportBtn.layoutXProperty().bind(gamePane.widthProperty().subtract(160));
        reportBtn.setLayoutY(8);
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
        gamePane.getChildren().add(reportBtn);

//        // 创建 MiniMap
//        miniMap = new MiniMap(
//                player.getType() == Player.PlayerType.EVIL ? evilMapBg : goodMapBg,
//                playerIcon,
//                GameConstants.MAP_WIDTH,
//                GameConstants.MAP_HEIGHT,
//                200, 150
//        );
//
//// 放到右上角
//        miniMap.layoutYProperty().set(50);
//        miniMap.layoutXProperty().bind(gamePane.widthProperty().subtract(200 + 8));
//        gamePane.getChildren().add(miniMap);
//
//// 绑定玩家位置属性
//        miniMap.bindPlayerPosition(player.xProperty(), player.yProperty());
//
//// 小地图按钮
//        Button miniMapBtn = new Button("小地图");
//        miniMapBtn.setPrefWidth(80);
//        miniMapBtn.setPrefHeight(30);
//
//// 让它在聊天按钮右边：直接绑定 X 坐标
//        miniMapBtn.layoutXProperty().bind(chatBtn.layoutXProperty().add(chatBtn.widthProperty()).add(8));
//        miniMapBtn.setLayoutY(8); // 同一行
//
//        gamePane.getChildren().add(miniMapBtn);
//        miniMapBtn.setOnAction(e -> {
//            boolean visible = !miniMap.isVisible();
//            miniMap.setVisible(visible);
//            if (visible) {
//                miniMap.toFront(); // 确保在最上层
//            } else {
//                gamePane.requestFocus(); // 回到游戏焦点
//            }
//        });
//
//
//        // 添加状态栏
//        gamePane.getChildren().add(statusBar);
//        statusBar.setLayoutX(20);
//        statusBar.setLayoutY(20);
//        statusBar.toFront();

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
            // Debug log for key press
            System.out.println("[DEBUG] Key pressed: " + e.getCode() + " (chat visible=" + chatPane.isVisible() + ")");
            // If chat visible, don't add to movement keys
            if (!chatPane.isVisible()) {
                inputHandler.press(e.getCode());
            }
            // hotkeys for tasks (allow even if chat visible? keep original behavior: no)
            if (!chatPane.isVisible()) {
                switch (e.getCode()) {
                    case T -> taskManager.tryStartTask("CardSwipe");
                    case F -> taskManager.tryStartTask("Download");
                    case G -> taskManager.tryStartTask("FixWiring");
                }
            }
            // keep original behavior: pressing F starts DownloadTask UI as test (you had this earlier)
            if (e.getCode() == KeyCode.F && !chatPane.isVisible()) {
                new DownloadTask().start();
            }
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

                for (TriggerZone z : taskManager.getZones()) {
                    z.getView().setX(z.getWorldX() + offsetX);
                    z.getView().setY(z.getWorldY() + offsetY);
                }

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

    // --- 网络消息处理 (handleNetworkMessage) ---
    private void handleNetworkMessage(Message.Parsed parsed) {
        if (parsed == null) return;

        switch (parsed.type) {
            case "JOIN": handleJoin(parsed); break;
            case "MOVE": handleMove(parsed); break;
            case "LEAVE": handleLeave(parsed); break;
            case "CHAT": handleChat(parsed); break;
            case "GAME_START": handleGameStart(parsed); break;
            case "MATCH_UPDATE": {
                int current = Integer.parseInt(parsed.payload.get("current"));
                int total = Integer.parseInt(parsed.payload.get("total"));
                if (matchUpdateListener != null) {
                    Platform.runLater(() -> matchUpdateListener.onMatchUpdate(current, total));
                }
                break;
            }
            case "MEETING_DISCUSSION_START": {
                int duration = Integer.parseInt(parsed.payload.getOrDefault("duration","120"));
                inMeeting = true; // 开始讨论
                addNodeToTop(chatPane);
                chatPane.show();
                startMeetingCountdown(duration, "讨论剩余: %d s");
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

                // ✅ 检查是否结束
                gameManager.checkGameOver();

                // 保留投票面板几秒，让玩家看到出局
                PauseTransition delay = new PauseTransition(Duration.seconds(4)); // 4秒停留
                delay.setOnFinished(ev -> {
                    inMeeting = false;
                    if (votePane != null && votePane.getParent() instanceof Pane) {
                        ((Pane) votePane.getParent()).getChildren().remove(votePane);
                    }
                    votePane = null;
                    chatPane.hide();
                    stopMeetingCountdown();
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
                // ✅ 检查是否结束
                gameManager.checkGameOver();

                // 不要修改 inMeeting
                break;
            }

            default: break;
        }
    }

    private void endMeetingCleanup() {
        if (votePane != null && votePane.getParent() instanceof Pane) {
            ((Pane) votePane.getParent()).getChildren().remove(votePane);
        }
        votePane = null;
        chatPane.hide();
        inMeeting = false;
        stopMeetingCountdown();
        // ensure focus returns to gamePane so keys work again
        Platform.runLater(() -> {
            try { gamePane.requestFocus(); } catch (Exception ignored) {}
        });
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
        RemotePlayer rp = remotePlayers.get(id);
        if(rp==null){
            Image img = new Image(getClass().getResourceAsStream("/com/edu/example/amongus/images/"+color+".png"));
            ImageView iv = new ImageView(img);
            iv.setFitWidth(GameConstants.PLAYER_SIZE);
            iv.setFitHeight(GameConstants.PLAYER_SIZE);
            rp = new RemotePlayer(id,nick,color,iv,x,y,PlayerStatus.ALIVE, RemotePlayer.PlayerType.GOOD);
            remotePlayers.put(id,rp);
            gamePane.getChildren().addAll(iv,rp.nameTag);
        } else {
            rp.nick = nick; rp.color=color; rp.nameTag.setText(nick);
            try { rp.view.setImage(new Image(getClass().getResourceAsStream("/com/edu/example/amongus/images/"+color+".png"))); }
            catch(Exception ignored){}
        }
    }

    private void handleMove(Message.Parsed parsed){
        String id = parsed.payload.get("id");
        if(id.equals(myId)) return;
        double x = Double.parseDouble(parsed.payload.getOrDefault("x","0"));
        double y = Double.parseDouble(parsed.payload.getOrDefault("y","0"));
        RemotePlayer rp = remotePlayers.get(id);
        if(rp!=null){ rp.x=x; rp.y=y;  }
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

    private static class RemotePlayer {
        String id, nick, color;
        ImageView view;
        Label nameTag;
        double x, y;
        PlayerStatus status = PlayerStatus.ALIVE;

        public enum PlayerType {
            GOOD, EVIL
        }
        PlayerType type; // 好人 / 坏人

        RemotePlayer(String id, String nick, String color, ImageView v, double x, double y,
                     PlayerStatus status, PlayerType type) {
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

        public Node getView() {
            return view;
        }
    }
    public Pane getGamePane() {
        return gamePane;
    }
}
