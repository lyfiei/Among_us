package com.edu.example.amongus.ui;

import com.edu.example.amongus.Player;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.util.Duration;
import javafx.scene.image.*;

import java.util.List;

public class PlayerActionUI {
    private final Button killButton;
    private final Label roleLabel;
    private final Pane parentPane;
    private final Player player;
    private final List<Player> allPlayers;
    private Timeline cooldownTimer;

    public PlayerActionUI(Player player, List<Player> allPlayers, Pane parentPane) {
        this.player = player;
        this.allPlayers = allPlayers;
        this.parentPane = parentPane;

        killButton = new Button();
        roleLabel = new Label();

        initializeUI();
        startCooldownTimer();
        updatePlayerType(); // 初始化显示状态
    }
    /** 初始化按钮和标签 */
    private void initializeUI() {
        // Kill按钮样式
        killButton.setFocusTraversable(false);
        killButton.setManaged(true);

        Image killImg = new Image(getClass().getResource("/com/edu/example/amongus/images/kill.png").toExternalForm());
        ImageView killView = new ImageView(killImg);
        killView.setFitWidth(50);  // 图片宽度
        killView.setFitHeight(50); // 图片高度
        killButton.setGraphic(killView);


        // 添加悬停效果
        killButton.setOnMouseEntered(e -> killButton.setStyle("-fx-background-color: darkred; -fx-text-fill: white;"));
        killButton.setOnMouseExited(e -> killButton.setStyle("-fx-background-color: red; -fx-text-fill: white;"));

        // 点击事件
        killButton.setOnAction(e -> {
            System.out.println("[DEBUG] 点击杀人按钮, 本地位置: (" + player.getX() + "," + player.getY() + ")");
            long remaining = player.getKillCooldownRemaining();
            if (remaining > 0) {
                System.out.println("[DEBUG] 冷却中，还要 " + remaining/1000 + " 秒");
                return;
            }

            if (player.killNearbyPlayer(allPlayers) != null) {
                System.out.println("[DEBUG] 杀人成功");
                updateKillButtonText();
            } else {
                System.out.println("[DEBUG] 杀人失败，附近没有可杀玩家");
            }
        });

        // 角色标签样式
        roleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        // 添加到Pane
        Platform.runLater(() -> {
            if (!parentPane.getChildren().contains(killButton)) parentPane.getChildren().add(killButton);
            if (!parentPane.getChildren().contains(roleLabel)) parentPane.getChildren().add(roleLabel);

            // 按钮位置绑定到右下角
            killButton.layoutXProperty().bind(parentPane.widthProperty().subtract(140));
            killButton.layoutYProperty().bind(parentPane.heightProperty().subtract(60));

            // 标签位置绑定到右上角
            roleLabel.layoutXProperty().bind(parentPane.widthProperty().subtract(120));
            roleLabel.setLayoutY(10);

            killButton.toFront();
            roleLabel.toFront();
        });
    }

    /** 初始化按钮和标签 */
//    private void initializeUI() {
//        // 创建杀人图片按钮
//        Image killImage = new Image(getClass().getResource("/com/edu/example/amongus/images/kill.png").toExternalForm());
//        ImageView killImageView = new ImageView(killImage);
//
//        // 设置图片大小
//        killImageView.setFitWidth(80);
//        killImageView.setFitHeight(80);
//        killImageView.setPreserveRatio(true);
//
//        // 鼠标悬停效果
//        killImageView.setOnMouseEntered(e -> killImageView.setOpacity(0.7));
//        killImageView.setOnMouseExited(e -> killImageView.setOpacity(1.0));
//
//        // 点击事件
//        killImageView.setOnMouseClicked(e -> {
//            System.out.println("[DEBUG] 点击杀人按钮, 本地位置: (" + player.getX() + "," + player.getY() + ")");
//            long remaining = player.getKillCooldownRemaining();
//            if (remaining > 0) {
//                System.out.println("[DEBUG] 冷却中，还要 " + remaining / 1000 + " 秒");
//                return;
//            }
//
//            if (player.killNearbyPlayer(allPlayers) != null) {
//                System.out.println("[DEBUG] 杀人成功");
//                updateKillButtonText(); // 如果要在图片上显示冷却文字，可以扩展这里
//            } else {
//                System.out.println("[DEBUG] 杀人失败，附近没有可杀玩家");
//            }
//        });
//
//        // 角色标签样式
//        roleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
//
//        Platform.runLater(() -> {
//            if (!parentPane.getChildren().contains(killImageView)) parentPane.getChildren().add(killImageView);
//            if (!parentPane.getChildren().contains(roleLabel)) parentPane.getChildren().add(roleLabel);
//
//            // 图片按钮位置绑定到右下角
//            killImageView.layoutXProperty().bind(parentPane.widthProperty().subtract(100));
//            killImageView.layoutYProperty().bind(parentPane.heightProperty().subtract(100));
//
//            // 标签位置绑定到右上角
//            roleLabel.layoutXProperty().bind(parentPane.widthProperty().subtract(120));
//            roleLabel.setLayoutY(10);
//
//            killImageView.toFront();
//            roleLabel.toFront();
//        });
//    }

    /** 冷却计时器 */
    private void startCooldownTimer() {
        cooldownTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateKillButtonText()));
        cooldownTimer.setCycleCount(Timeline.INDEFINITE);
        cooldownTimer.play();
    }

    /** 更新按钮文字和状态 */
    private void updateKillButtonText() {
        Platform.runLater(() -> {
            if (player.getType() == Player.PlayerType.EVIL) {
                long remaining = player.getKillCooldownRemaining();
                if (remaining > 0) {
                    killButton.setText("Kill (" + remaining/1000 + "s)");
                    killButton.setDisable(true);
                } else {
                    killButton.setText("Kill");
                    killButton.setDisable(false);
                }
            }
        });
    }

    /** 更新角色标签和按钮显示 */
    public void updatePlayerType() {
        Platform.runLater(() -> {
            boolean isEvil = player.getType() == Player.PlayerType.EVIL;

            // 控制按钮可见性
            killButton.setVisible(isEvil);
            if (isEvil) killButton.toFront();

            // 更新按钮文本
            updateKillButtonText();

            // 更新角色标签
            String roleText = isEvil ? "伪装者" : "好人";
            String style = "-fx-font-size: 18px; -fx-font-weight: bold; " +
                    (isEvil ? "-fx-text-fill: red;" : "-fx-text-fill: green;");
            roleLabel.setText(roleText);
            roleLabel.setStyle(style);
            roleLabel.toFront();
        });
    }

    public Button getKillButton() {
        return killButton;
    }

    public Label getRoleLabel() {
        return roleLabel;
    }
}
