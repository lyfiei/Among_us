package com.edu.example.amongus.ui;

import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;

public class MatchUI extends VBox implements MatchUpdateListener{
    private Label statusLabel;
    private ProgressBar progressBar;


    public MatchUI (int total) {
        // 上方图片
        ImageView amongusImg = new ImageView(
                new Image(getClass().getResource("/com/edu/example/amongus/images/Among_Us_logo.png").toExternalForm())
        );
        amongusImg.setFitWidth(150);
        amongusImg.setPreserveRatio(true);

        // 中间进度条
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(300);

        // 下方文字
        statusLabel = new Label("已匹配 0/" + total + " 位玩家");
        statusLabel.setStyle("-fx-text-fill: white; -fx-font-size: 18px;");

        // 布局
        setSpacing(15);
        setAlignment(Pos.CENTER);
        setStyle("-fx-background-color: black;");
        getChildren().addAll(amongusImg, progressBar, statusLabel);
    }

    public void onMatchUpdate(int current, int total) {
        statusLabel.setText("已匹配 " + current + "/" + total + " 位玩家");
        progressBar.setProgress((double) current / total);
    }
}
