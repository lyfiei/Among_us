package com.edu.example.amongus.ui;

import com.edu.example.amongus.GameConstants;
import com.edu.example.amongus.PlayerStatus;
import com.edu.example.amongus.net.GameClient;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 投票面板（支持显示票数）
 */
public class VotePane extends VBox {
    private final GameClient client;
    private final String myId;
    private final Map<String, PlayerItem> players = new HashMap<>();
    private final Label timerLabel = new Label();
    private Timeline countdown;
    private int timeLeft = 30;

    public VotePane(GameClient client, String myId) {
        this.client = client;
        this.myId = myId;

        setSpacing(10);
        setAlignment(Pos.TOP_CENTER);
        setStyle("-fx-background-color: rgba(30,30,30,0.95); -fx-padding: 12; -fx-border-color: #444; -fx-border-width: 2; -fx-background-radius:8;");

        Label title = new Label("投票 — 请选择一名玩家");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: white;");
        timerLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: yellow;");

        getChildren().addAll(title, timerLabel);
    }

    /**
     * 添加玩家到投票列表
     */
    public void addPlayer(String id, String nick, String color, PlayerStatus status) {
        if (players.containsKey(id)) return;

        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);

        ImageView avatar = new ImageView();
        avatar.setFitWidth(GameConstants.PLAYER_SIZE);
        avatar.setFitHeight(GameConstants.PLAYER_SIZE);

        Label nameLabel = new Label(nick);
        nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: white;");

        try {
            String path = "/com/edu/example/amongus/images/" + color + ".png";
            Image img = new Image(getClass().getResourceAsStream(path));
            avatar.setImage(img);
        } catch (Exception e) {
            System.err.println("VotePane: 加载头像失败: " + color + "，使用默认占位。");
        }

        Button voteBtn = new Button("投票");
        PlayerItem item = new PlayerItem(id, nick, color, avatar, nameLabel, voteBtn);

        // 死亡玩家不能投票
        if (status == PlayerStatus.DEAD) {
            item.setDead();
        } else if (!id.equals(myId)) {
            voteBtn.setOnAction(e -> sendVote(id));
        } else {
            voteBtn.setDisable(true); // 自己不能投自己
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().addAll(avatar, nameLabel, spacer, voteBtn);

        getChildren().add(row);
        players.put(id, item);
    }

    /**
     * 发送投票
     */
    private void sendVote(String targetId) {
        PlayerItem self = players.get(myId);
        if (self == null || self.status == PlayerStatus.DEAD) {
            System.out.println("[DEBUG] 已淘汰玩家不能投票");
            return;
        }

        if (client == null) return;
        Map<String, String> payload = new HashMap<>();
        payload.put("voter", myId);
        payload.put("target", targetId);
        try {
            client.send("VOTE", payload);

            // 投票后禁用自己的所有投票按钮
            players.values().forEach(p -> p.voteBtn.setDisable(true));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 投票倒计时
     */
    public void startCountdown(int seconds) {
        this.timeLeft = seconds;
        if (countdown != null) countdown.stop();

        timerLabel.setText("投票剩余: " + timeLeft + " 秒");
        countdown = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            timeLeft--;
            timerLabel.setText("投票剩余: " + timeLeft + " 秒");
            if (timeLeft <= 0) {
                countdown.stop();
                players.values().forEach(p -> p.voteBtn.setDisable(true));
                timerLabel.setText("投票结束，等待结果...");
            }
        }));
        countdown.setCycleCount(seconds);
        countdown.play();
    }

    /**
     * 更新投票票数
     */
    public void registerVoteUpdate(String voterId, String targetId) {
        Platform.runLater(() -> {
            PlayerItem target = players.get(targetId);
            if (target != null && target.status != PlayerStatus.DEAD) {
                target.voteCount++;
                target.nameLabel.setText(target.nick + " ✓" + target.voteCount);
            }
        });
    }

    /**
     * 显示投票结果
     */
    public void showVoteResult(String votedOutId) {
        Platform.runLater(() -> {
            // 禁用所有按钮
            players.values().forEach(p -> p.voteBtn.setDisable(true));

            // 被投出玩家标记为死亡
            if (votedOutId != null) {
                PlayerItem out = players.get(votedOutId);
                if (out != null) out.setDead();
            }

            // 显示投票结果
            VBox resultBox = new VBox(8);
            resultBox.setAlignment(Pos.CENTER);
            resultBox.setStyle("-fx-background-color: rgba(0,0,0,0.8); -fx-padding: 14; -fx-background-radius:6;");

            Label resultLabel;
            if (votedOutId != null) {
                PlayerItem out = players.get(votedOutId);
                resultLabel = new Label(out.nick + " 被投出！");
                if (out != null && out.avatar.getImage() != null) {
                    ImageView big = new ImageView(out.avatar.getImage());
                    big.setFitWidth(64);
                    big.setFitHeight(64);
                    resultBox.getChildren().add(big);
                }
            } else {
                resultLabel = new Label("无人被投出");
            }
            resultLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: yellow;");
            resultBox.getChildren().add(resultLabel);

            getChildren().add(resultBox);

            // 自动移除投票面板
            PauseTransition delay = new PauseTransition(Duration.seconds(4));
            delay.setOnFinished(ev -> {
                if (getParent() instanceof Pane) ((Pane) getParent()).getChildren().remove(this);
            });
            delay.play();
        });
    }

    /**
     * 玩家条目
     */
    private static class PlayerItem {
        String id, nick, color;
        ImageView avatar;
        Label nameLabel;
        Button voteBtn;
        PlayerStatus status = PlayerStatus.ALIVE;
        int voteCount = 0;

        PlayerItem(String id, String nick, String color, ImageView avatar, Label nameLabel, Button voteBtn) {
            this.id = id;
            this.nick = nick;
            this.color = color;
            this.avatar = avatar;
            this.nameLabel = nameLabel;
            this.voteBtn = voteBtn;
        }

        void setDead() {
            status = PlayerStatus.DEAD;
            nameLabel.setText(nick + " (已出局)");
            nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: gray;");
            avatar.setOpacity(0.4);
            voteBtn.setDisable(true);
        }
    }
}
