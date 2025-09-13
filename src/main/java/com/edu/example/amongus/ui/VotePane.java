package com.edu.example.amongus.ui;

import com.edu.example.amongus.GameConstants;
import com.edu.example.amongus.PlayerStatus;
import com.edu.example.amongus.net.GameClient;
import javafx.animation.KeyFrame;
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
 * 投票面板。每次开投票时由 GameApp 新建（或重新建）以保证状态正确。
 */
public class VotePane extends VBox {
    private final GameClient client;
    private final String myId;
    private final Map<String, PlayerItem> players = new HashMap<>();
    private final Label timerLabel = new Label();
    private Timeline countdown;
    private int timeLeft = 30; // 默认倒计时

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
     * 添加玩家到投票列表。传入玩家当前状态（ALIVE/DEAD），dead 会灰化并禁用该玩家投票按钮。
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

        // 如果玩家已出局 -> 半透明 + 名字灰色 + 禁用投票
        Button voteBtn = new Button("投票");
        if (status == PlayerStatus.DEAD) {
            avatar.setOpacity(0.4);
            nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: gray;");
            voteBtn.setDisable(true);
        } else {
            voteBtn.setOnAction(e -> sendVote(id));
            // 如果是自己且已经出局（理论上不会到这里），也禁用
            if (id.equals(myId)) {
                // 仍允许自己投票（如果规则允许），否则注释下一行
                // voteBtn.setDisable(true);
            }
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        row.getChildren().addAll(avatar, nameLabel, spacer, voteBtn);
        getChildren().add(row);

        players.put(id, new PlayerItem(id, nick, color, avatar, nameLabel, voteBtn));
    }

    private void sendVote(String targetId) {
        if (client == null) return;
        Map<String, String> payload = new HashMap<>();
        payload.put("voter", myId);
        payload.put("target", targetId);
        try {
            client.send("VOTE", payload);
            // 本地禁用所有按钮以防重复投票
            players.values().forEach(p -> p.voteBtn.setDisable(true));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

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

    /** 服务器广播某人已投票：UI 简短标记 */
    public void registerVoteUpdate(String voterId, String targetId) {
        Platform.runLater(() -> {
            PlayerItem voter = players.get(voterId);
            if (voter != null) voter.nameLabel.setText(voter.nick + " ✓");
        });
    }

    /** 显示最终结果（高亮被投出的玩家，并在该面板也显示头像/提示） */
    public void showVoteResult(String votedOutId, String myId) {
        Platform.runLater(() -> {
            // 标记样式并禁用按钮
            for (PlayerItem p : players.values()) {
                if (p.id.equals(votedOutId)) {
                    p.nameLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: red;");
                    p.avatar.setOpacity(0.4);
                } else {
                    p.nameLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: gray;");
                }
                p.voteBtn.setDisable(true);
            }

            // 显示结果摘要框
            VBox resultBox = new VBox(8);
            resultBox.setAlignment(Pos.CENTER);
            resultBox.setStyle("-fx-background-color: rgba(0,0,0,0.8); -fx-padding: 14; -fx-background-radius:6;");

            Label resultLabel;
            if (votedOutId == null || votedOutId.isEmpty()) {
                resultLabel = new Label("无人被投出（平票或未投票）");
            } else if (votedOutId.equals(myId)) {
                resultLabel = new Label("你已出局！");
            } else {
                PlayerItem out = players.get(votedOutId);
                String nick = out != null ? out.nick : votedOutId;
                resultLabel = new Label(nick + " 被投出！");
            }
            resultLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: white;");

            if (votedOutId != null && !votedOutId.isEmpty()) {
                PlayerItem out = players.get(votedOutId);
                if (out != null && out.avatar != null && out.avatar.getImage() != null) {
                    ImageView big = new ImageView(out.avatar.getImage());
                    big.setFitWidth(64);
                    big.setFitHeight(64);
                    resultBox.getChildren().add(big);
                }
            }
            resultBox.getChildren().add(resultLabel);
            getChildren().add(resultBox);

            timerLabel.setText("投票结束");
        });
    }

    private static class PlayerItem {
        String id, nick, color;
        ImageView avatar;
        Label nameLabel;
        Button voteBtn;
        PlayerItem(String id, String nick, String color, ImageView avatar, Label nameLabel, Button voteBtn) {
            this.id = id; this.nick = nick; this.color = color;
            this.avatar = avatar; this.nameLabel = nameLabel; this.voteBtn = voteBtn;
        }
    }
}
