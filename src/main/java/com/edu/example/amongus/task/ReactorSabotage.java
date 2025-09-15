package com.edu.example.amongus.task;

import com.edu.example.amongus.GameConstants;
import com.edu.example.amongus.net.Message;
import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;
import com.edu.example.amongus.GameApp;
import com.edu.example.amongus.Player;
import com.edu.example.amongus.net.GameClient;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ReactorSabotage {
    private final GameApp gameApp;
    private final Pane gamePane;
    private final GameClient client;
    private final Player player;

    // 修复区域
    private final TriggerZone leftFixZone;
    private final TriggerZone rightFixZone;

    // 状态
    private boolean sabotageActive = false;
    private boolean leftFixed = false;
    private boolean rightFixed = false;
    private int repairCount = 0;

    // UI元素
    private Rectangle redOverlay;
    private Label countdownLabel;
    private Timeline countdownTimer;

    // 常量
    private static final int SABOTAGE_DURATION = 120; // 2分钟
    private static final double LEFT_FIX_X = 1500;
    private static final double LEFT_FIX_Y = 600;
    private static final double RIGHT_FIX_X = 1800;
    private static final double RIGHT_FIX_Y = 600;
    private static final double FIX_ZONE_SIZE = 100;

    public ReactorSabotage(GameApp gameApp) {
        this.gameApp = gameApp;
        this.gamePane = gameApp.getGamePane();
        this.client = gameApp.getClient();
        this.player = gameApp.getPlayer();

        if (this.player == null) {
            throw new IllegalStateException("Player cannot be null in ReactorSabotage");
        }

        // 创建修复区域
        this.leftFixZone = new TriggerZone(LEFT_FIX_X, LEFT_FIX_Y, FIX_ZONE_SIZE, FIX_ZONE_SIZE, "LeftReactorFix");
        this.rightFixZone = new TriggerZone(RIGHT_FIX_X, RIGHT_FIX_Y, FIX_ZONE_SIZE, FIX_ZONE_SIZE, "RightReactorFix");

        // 初始化UI元素
        initUI();
    }

    private void initUI() {
        // 红色覆盖层
        redOverlay = new Rectangle(GameConstants.MAP_WIDTH, GameConstants.MAP_HEIGHT);
        redOverlay.setFill(Color.rgb(255, 0, 0, 0.3));
        redOverlay.setVisible(false);
        gamePane.getChildren().add(redOverlay);

        // 倒计时标签
        countdownLabel = new Label();
        countdownLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: white; -fx-background-color: rgba(0,0,0,0.7);");
        countdownLabel.setVisible(false);
        gamePane.getChildren().add(countdownLabel);

        // 修复区域高亮
        leftFixZone.getView().setFill(Color.rgb(0, 255, 0, 0.5));
        rightFixZone.getView().setFill(Color.rgb(0, 255, 0, 0.5));
        leftFixZone.getView().setVisible(false);
        rightFixZone.getView().setVisible(false);
        gamePane.getChildren().addAll(leftFixZone.getView(), rightFixZone.getView());
    }

    // 坏人按G键触发破坏事件
    // 使用完全限定名确保类型正确
    public void handleKeyPress(javafx.scene.input.KeyCode code) {
        if (player == null) {
            System.err.println("Player is null in ReactorSabotage!");
            return;
        }
        if (code == javafx.scene.input.KeyCode.G && player.getType() == Player.PlayerType.EVIL && !sabotageActive) {
            startSabotage();
        }

        if (code == javafx.scene.input.KeyCode.H && sabotageActive && player.getType() == Player.PlayerType.GOOD) {
            attemptFix();
        }
    }

    private void startSabotage() {
        sabotageActive = true;
        leftFixed = false;
        rightFixed = false;
        repairCount = 0;

        // 显示红色覆盖层和闪烁效果
        redOverlay.setVisible(true);
        startFlashingEffect();

        // 显示修复区域
        leftFixZone.getView().setVisible(true);
        rightFixZone.getView().setVisible(true);

        // 显示倒计时
        countdownLabel.setVisible(true);
        startCountdown();

        // 通知服务器
        if (client != null) {
            try {
                Map<String, String> payload = new HashMap<>();
                payload.put("sabotage", "reactor");
                payload.put("duration", String.valueOf(SABOTAGE_DURATION));
                client.send("SABOTAGE", payload);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void startFlashingEffect() {
        AnimationTimer flashTimer = new AnimationTimer() {
            private long lastToggle = 0;

            @Override
            public void handle(long now) {
                if (!sabotageActive) {
                    stop();
                    redOverlay.setVisible(false);
                    return;
                }

                if (now - lastToggle > 500_000_000) { // 每0.5秒切换一次
                    redOverlay.setVisible(!redOverlay.isVisible());
                    lastToggle = now;
                }
            }
        };
        flashTimer.start();
    }

    private void startCountdown() {
        AtomicInteger secondsLeft = new AtomicInteger(SABOTAGE_DURATION);
        countdownLabel.setText("修复剩余时间: " + secondsLeft.get() + "秒");

        countdownTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            int left = secondsLeft.decrementAndGet();
            countdownLabel.setText("修复剩余时间: " + left + "秒");

            // 更新位置跟随玩家
            countdownLabel.setLayoutX(player.getX() - 100);
            countdownLabel.setLayoutY(player.getY() - 50);

            if (left <= 0) {
                sabotageFailed();
            }
        }));
        countdownTimer.setCycleCount(SABOTAGE_DURATION);
        countdownTimer.play();
    }

    private void attemptFix() {
        // 检查玩家是否在修复区域内
        boolean inLeftZone = leftFixZone.isPlayerInside(player);
        boolean inRightZone = rightFixZone.isPlayerInside(player);

        if (!inLeftZone && !inRightZone) {
            return;
        }

        // 防止重复修复
        if ((inLeftZone && leftFixed) || (inRightZone && rightFixed)) {
            return;
        }

        // 标记修复
        if (inLeftZone) {
            leftFixed = true;
        } else {
            rightFixed = true;
        }

        repairCount++;

        // 通知服务器
        if (client != null) {
            try {
                Map<String, String> payload = new HashMap<>();
                payload.put("player", player.getId());
                payload.put("side", inLeftZone ? "left" : "right");
                client.send("REACTOR_FIX", payload);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // 检查是否完成修复
        if (repairCount >= 2) {
            sabotageFixed();
        }
    }

    private void sabotageFixed() {
        // 停止倒计时和闪烁
        sabotageActive = false;
        if (countdownTimer != null) {
            countdownTimer.stop();
        }

        // 隐藏UI元素
        redOverlay.setVisible(false);
        countdownLabel.setVisible(false);
        leftFixZone.getView().setVisible(false);
        rightFixZone.getView().setVisible(false);

        // 播放修复成功动画
        playSuccessAnimation();
    }

    private void sabotageFailed() {
        // 停止倒计时和闪烁
        sabotageActive = false;
        if (countdownTimer != null) {
            countdownTimer.stop();
        }

        // 隐藏UI元素
        redOverlay.setVisible(false);
        countdownLabel.setVisible(false);
        leftFixZone.getView().setVisible(false);
        rightFixZone.getView().setVisible(false);

        // 通知服务器好人失败
        if (client != null) {
            try {
                Map<String, String> payload = new HashMap<>();
                payload.put("result", "fail");
                client.send("SABOTAGE_RESULT", payload);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void playSuccessAnimation() {
        try {
            // 加载视频文件
            String videoFile = new File("src/main/resources/com/edu/example/amongus/videos/reactor_fixed.mp4").toURI().toString();
            Media media = new Media(videoFile);
            MediaPlayer mediaPlayer = new MediaPlayer(media);
            MediaView mediaView = new MediaView(mediaPlayer);

            // 创建新窗口播放视频
            Stage videoStage = new Stage();
            Pane videoPane = new Pane(mediaView);
            Scene videoScene = new Scene(videoPane, 640, 480);
            videoStage.setScene(videoScene);
            videoStage.setTitle("反应堆修复成功");
            videoStage.show();

            // 视频播放完成后关闭窗口
            mediaPlayer.setOnEndOfMedia(() -> {
                Platform.runLater(videoStage::close);
            });

            mediaPlayer.play();
        } catch (Exception e) {
            e.printStackTrace();
            // 如果视频播放失败，显示一个简单的成功消息
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("修复成功");
                alert.setHeaderText("核反应堆已稳定");
                alert.setContentText("团队合作成功防止了反应堆熔毁！");
                alert.show();
            });
        }
    }

    // 处理网络消息
    public void handleNetworkMessage(Message.Parsed parsed) {
        if (parsed == null) return;

        switch (parsed.type) {
            case "SABOTAGE":
                if (parsed.payload.get("sabotage").equals("reactor")) {
                    // 如果是好人收到破坏通知
                    if (player.getType() == Player.PlayerType.GOOD) {
                        Platform.runLater(() -> {
                            startSabotage();
                        });
                    }
                }
                break;

            case "REACTOR_FIX":
                String side = parsed.payload.get("side");
                if (side.equals("left")) {
                    leftFixed = true;
                } else {
                    rightFixed = true;
                }
                repairCount++;

                if (repairCount >= 2) {
                    Platform.runLater(() -> {
                        sabotageFixed();
                    });
                }
                break;

            case "SABOTAGE_RESULT":
                if (parsed.payload.get("result").equals("fail")) {
                    Platform.runLater(() -> {
                        // 显示失败消息
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("修复失败");
                        alert.setHeaderText("反应堆熔毁");
                        alert.setContentText("好人未能及时修复反应堆，坏人获胜！");
                        alert.show();
                    });
                }
                break;
        }
    }
}