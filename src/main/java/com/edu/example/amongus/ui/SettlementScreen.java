package com.edu.example.amongus.ui;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.Label;
import javafx.geometry.Pos;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.geometry.Insets;
import javafx.scene.media.MediaPlayer;
import javafx.stage.Stage;
import javafx.scene.media.Media;

import java.util.List;
import java.util.Map;

public class SettlementScreen extends StackPane {
    private static final Map<String, String> COLOR_ICON_MAP = Map.of(
            "red", "/com/edu/example/amongus/images/red.png",
            "blue", "/com/edu/example/amongus/images/blue.png",
            "green", "/com/edu/example/amongus/images/green.png",
            "yellow", "/com/edu/example/amongus/images/yellow.png",
            "purple", "/com/edu/example/amongus/images/purple.png"
    );

    private MediaPlayer mediaPlayer; // <- 增加成员变量

    public SettlementScreen(String message, List<Map<String, String>> evilPlayers) {

        boolean isVictory = message.contains("胜利");

        // ========== 背景 ==========
        String backgroundPath = isVictory
                ? "/com/edu/example/amongus/images/victory_background.jpg"
                : "/com/edu/example/amongus/images/defeat_background.jpg";

        try {
            Image backgroundImage = new Image(getClass().getResourceAsStream(backgroundPath));
            ImageView backgroundView = new ImageView(backgroundImage);
            backgroundView.fitWidthProperty().bind(this.widthProperty());
            backgroundView.fitHeightProperty().bind(this.heightProperty());
            this.getChildren().add(backgroundView);
        } catch (Exception e) {
            System.err.println("加载背景图片失败: " + e.getMessage());
        }

        // ========== 中间内容 ==========
        Label messageLabel = new Label(message);
        messageLabel.setStyle("-fx-font-size: 36px; -fx-text-fill: white; -fx-font-weight: bold;");

        VBox contentBox = new VBox(20);
        contentBox.setAlignment(Pos.CENTER);
        contentBox.getChildren().add(messageLabel);

        Label evilTitleLabel = new Label("坏人玩家：");
        evilTitleLabel.setStyle("-fx-font-size: 28px; -fx-text-fill: white; -fx-font-weight: bold;");
        contentBox.getChildren().add(evilTitleLabel);

        VBox evilPlayersContainer = new VBox(10);
        evilPlayersContainer.setAlignment(Pos.CENTER);
        evilPlayersContainer.setPadding(new Insets(10, 0, 0, 0));

        for (Map<String, String> info : evilPlayers) {
            String nick = info.get("nick");
            String color = info.get("color");
            String iconPath = COLOR_ICON_MAP.getOrDefault(color, "/com/edu/example/amongus/images/default_icon.png");

            HBox playerInfoBox = new HBox(10);
            playerInfoBox.setAlignment(Pos.CENTER);

            try {
                Image colorIcon = new Image(getClass().getResourceAsStream(iconPath));
                ImageView iconView = new ImageView(colorIcon);
                iconView.setFitWidth(30);
                iconView.setFitHeight(30);
                playerInfoBox.getChildren().add(iconView);
            } catch (Exception e) {
                Label placeholder = new Label("[图片缺失]");
                placeholder.setStyle("-fx-text-fill: gray;");
                playerInfoBox.getChildren().add(placeholder);
            }

            Label playerNameLabel = new Label(nick);
            playerNameLabel.setStyle("-fx-font-size: 24px; -fx-text-fill: white;");
            playerInfoBox.getChildren().add(playerNameLabel);

            evilPlayersContainer.getChildren().add(playerInfoBox);
        }
        contentBox.getChildren().add(evilPlayersContainer);

        // ========== 底部按钮 ==========
        ImageView exitImage = new ImageView(
                new Image(getClass().getResourceAsStream("/com/edu/example/amongus/images/exit.jpg"))
        );
        exitImage.setFitWidth(60);
        exitImage.setFitHeight(60);
        Button exitBtn = new Button();
        exitBtn.setGraphic(exitImage);
        exitBtn.setStyle("-fx-background-color: transparent;");
        exitBtn.setOnAction(e -> {
            stopAudio();
            Platform.exit();
        });

        ImageView backImage = new ImageView(
                new Image(getClass().getResourceAsStream("/com/edu/example/amongus/images/home.jpg"))
        );
        backImage.setFitWidth(60);
        backImage.setFitHeight(60);
        Button backBtn = new Button();
        backBtn.setGraphic(backImage);
        backBtn.setStyle("-fx-background-color: transparent;");
        backBtn.setOnAction(e -> {
            stopAudio(); // <- 点击返回停止音频
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/edu/example/amongus/start_menu.fxml"));
                Parent startMenu = loader.load();
                Stage stage = (Stage) getScene().getWindow();
                stage.setScene(new Scene(startMenu, 1000, 600));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        BorderPane bottomPane = new BorderPane();
        bottomPane.setLeft(exitBtn);
        bottomPane.setRight(backBtn);
        BorderPane.setMargin(exitBtn, new Insets(10));
        BorderPane.setMargin(backBtn, new Insets(10));

        this.getChildren().addAll(contentBox, bottomPane);
        StackPane.setAlignment(contentBox, Pos.CENTER);
        StackPane.setAlignment(bottomPane, Pos.BOTTOM_CENTER);

        // ========== 播放胜利/失败音效 ==========
        String audioPath = isVictory
                ? getClass().getResource("/com/edu/example/amongus/audio/Crewmate_victory_music.mp3").toExternalForm()
                : getClass().getResource("/com/edu/example/amongus/audio/Impostor_victory.mp3").toExternalForm();

        Media sound = new Media(audioPath);
        mediaPlayer = new MediaPlayer(sound);
        mediaPlayer.setCycleCount(MediaPlayer.INDEFINITE);
        mediaPlayer.play();
    }

    private void stopAudio() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
        }
    }
}


//public class SettlementScreen extends StackPane {
//    private static final Map<String, String> COLOR_ICON_MAP = Map.of(
//            "red", "/com/edu/example/amongus/images/red.png",
//            "blue", "/com/edu/example/amongus/images/blue.png",
//            "green", "/com/edu/example/amongus/images/green.png",
//            "yellow", "/com/edu/example/amongus/images/yellow.png",
//            "purple", "/com/edu/example/amongus/images/purple.png"
//            // 添加你游戏中所有可能的颜色及其对应的图标路径
//    );
//
//    public SettlementScreen(String message, List<Map<String, String>> evilPlayers) {
//
//        // ========== 背景 ==========
//        boolean isVictory = message.contains("胜利");
//        String backgroundPath = isVictory
//                ? "/com/edu/example/amongus/images/victory_background.jpg"
//                : "/com/edu/example/amongus/images/defeat_background.jpg";
//
//        try {
//            Image backgroundImage = new Image(getClass().getResourceAsStream(backgroundPath));
//            ImageView backgroundView = new ImageView(backgroundImage);
//            backgroundView.fitWidthProperty().bind(this.widthProperty());
//            backgroundView.fitHeightProperty().bind(this.heightProperty());
//            this.getChildren().add(backgroundView);
//        } catch (Exception e) {
//            System.err.println("加载背景图片失败: " + e.getMessage());
//        }
//
//        // ========== 中间内容 ==========
//        Label messageLabel = new Label(message);
//        messageLabel.setStyle("-fx-font-size: 36px; -fx-text-fill: white; -fx-font-weight: bold;");
//
//        VBox contentBox = new VBox(20);
//        contentBox.setAlignment(Pos.CENTER);
//        contentBox.getChildren().add(messageLabel);
//
//        Label evilTitleLabel = new Label("坏人玩家：");
//        evilTitleLabel.setStyle("-fx-font-size: 28px; -fx-text-fill: white; -fx-font-weight: bold;");
//        contentBox.getChildren().add(evilTitleLabel);
//
//        VBox evilPlayersContainer = new VBox(10);
//        evilPlayersContainer.setAlignment(Pos.CENTER);
//        evilPlayersContainer.setPadding(new Insets(10, 0, 0, 0));
//
//        for (Map<String, String> info : evilPlayers) {
//            String nick = info.get("nick");
//            String color = info.get("color");
//            String iconPath = COLOR_ICON_MAP.getOrDefault(
//                    color,
//                    "/com/edu/example/amongus/images/default_icon.png"
//            );
//
//            HBox playerInfoBox = new HBox(10);
//            playerInfoBox.setAlignment(Pos.CENTER);
//
//            try {
//                Image colorIcon = new Image(getClass().getResourceAsStream(iconPath));
//                ImageView iconView = new ImageView(colorIcon);
//                iconView.setFitWidth(30);
//                iconView.setFitHeight(30);
//                playerInfoBox.getChildren().add(iconView);
//            } catch (Exception e) {
//                Label placeholder = new Label("[图片缺失]");
//                placeholder.setStyle("-fx-text-fill: gray;");
//                playerInfoBox.getChildren().add(placeholder);
//            }
//
//            Label playerNameLabel = new Label(nick);
//            playerNameLabel.setStyle("-fx-font-size: 24px; -fx-text-fill: white;");
//            playerInfoBox.getChildren().add(playerNameLabel);
//
//            evilPlayersContainer.getChildren().add(playerInfoBox);
//        }
//        contentBox.getChildren().add(evilPlayersContainer);
//
//        // ========== 底部按钮 ==========
//        ImageView exitImage = new ImageView(
//                new Image(getClass().getResourceAsStream("/com/edu/example/amongus/images/exit.jpg"))
//        );
//        exitImage.setFitWidth(60);
//        exitImage.setFitHeight(60);
//        Button exitBtn = new Button();
//        exitBtn.setGraphic(exitImage);
//        exitBtn.setStyle("-fx-background-color: transparent;");
//        exitBtn.setOnAction(e -> Platform.exit());
//
//        ImageView backImage = new ImageView(
//                new Image(getClass().getResourceAsStream("/com/edu/example/amongus/images/home.jpg"))
//        );
//        backImage.setFitWidth(60);
//        backImage.setFitHeight(60);
//        Button backBtn = new Button();
//        backBtn.setGraphic(backImage);
//        backBtn.setStyle("-fx-background-color: transparent;");
//        backBtn.setOnAction(e -> {
//            try {
//                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/edu/example/amongus/start_menu.fxml"));
//                Parent startMenu = loader.load();
//                Stage stage = (Stage) getScene().getWindow();
//                stage.setScene(new Scene(startMenu, 1000, 600));
//            } catch (Exception ex) {
//                ex.printStackTrace();
//            }
//        });
//
//        BorderPane bottomPane = new BorderPane();
//        bottomPane.setLeft(exitBtn);
//        bottomPane.setRight(backBtn);
//        BorderPane.setMargin(exitBtn, new Insets(10));
//        BorderPane.setMargin(backBtn, new Insets(10));
//
//        // ========== 把内容和按钮叠加到 StackPane ==========
//        this.getChildren().addAll(contentBox, bottomPane);
//
//        StackPane.setAlignment(contentBox, Pos.CENTER);
//        StackPane.setAlignment(bottomPane, Pos.BOTTOM_CENTER);
//    }
//}


//        // 显示坏人玩家列表
//        StringBuilder sb = new StringBuilder("坏人玩家：\n");
//        for (Map<String, String> info : evilPlayers) {
//            sb.append(info.get("nick")).append(" (").append(info.get("color")).append(")\n");
//        }
//        Label evilPlayersLabel = new Label(sb.toString());
//        evilPlayersLabel.setStyle("-fx-font-size: 24px; -fx-text-fill: white;");
//
//        // 将所有元素添加到 StackPane
//        // 你可以使用 VBox 或其他布局容器来更好地控制位置
//        this.setAlignment(Pos.CENTER);
//        this.getChildren().addAll(messageLabel, evilPlayersLabel);
//    }
//}
