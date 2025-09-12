package com.edu.example.amongus;

import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.Stage;

public class DownloadTask implements Task {
    private final Stage stage;
    private MediaPlayer mediaPlayer;
    private boolean active = false;      // 是否正在进行
    private boolean completed = false;   // 是否完成
    private TaskCompleteListener listener;

    public DownloadTask() {
        stage = new Stage();

        // 创建根容器，设置窗口大小
        Pane root = new Pane();
        root.setPrefSize(800, 600);

        // 背景图片
        var bgUrl = getClass().getResource("/com/edu/example/amongus/images/download_bg.png");
        if (bgUrl == null) {
            throw new RuntimeException("背景图片没找到");
        }
        ImageView bg = new ImageView(new Image(bgUrl.toExternalForm()));
        bg.setFitWidth(800);
        bg.setFitHeight(600);
        root.getChildren().add(bg);

        // 视频
        MediaView mediaView = new MediaView();
        mediaView.setFitWidth(800);
        mediaView.setFitHeight(600);
        mediaView.setVisible(false); // 初始隐藏
        root.getChildren().add(mediaView);

        // 点击图片播放视频
        bg.setOnMouseClicked(e -> {
            var videoUrl = getClass().getResource("/com/edu/example/amongus/videos/download.mp4");
            if (videoUrl == null) {
                System.err.println("视频文件没找到");
                return;
            }
            if (mediaPlayer == null) {
                Media media = new Media(videoUrl.toExternalForm());
                mediaPlayer = new MediaPlayer(media);
                mediaView.setMediaPlayer(mediaPlayer);
            }
            mediaView.setVisible(true);
            mediaPlayer.stop(); // 保证可以多次点击播放
            mediaPlayer.play();

            // 播放完成后标记任务完成
            mediaPlayer.setOnEndOfMedia(() -> complete());
        });

        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.setTitle("下载任务");
    }

    @Override
    public void start() {
        if (active || completed) return; // 已经在做或完成就不再重复开启
        active = true;
        completed = false;
        stage.show();
    }

    @Override
    public void complete() {
        active = false;
        completed = true;
        stage.close();
        if (listener != null) listener.onTaskComplete(true);
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public boolean isCompleted() {
        return completed;
    }

    @Override
    public void setTaskCompleteListener(TaskCompleteListener listener) {
        this.listener = listener;
    }
}
