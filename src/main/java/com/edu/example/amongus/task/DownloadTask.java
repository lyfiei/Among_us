package com.edu.example.amongus.task;

import com.edu.example.amongus.net.NetTaskManager;
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
    private boolean active = false;          // 是否正在进行
    private final int totalSteps;         // 总下载次数
    private int completedSteps = 0;       // 已完成次数
    private TaskCompleteListener listener;
    private final NetTaskManager netTaskManager;
    private final String taskName;

    public DownloadTask(String taskName, int totalSteps,NetTaskManager netTaskManager) {
        this.totalSteps = totalSteps;
        this.netTaskManager = netTaskManager;
        this.taskName = taskName;
        stage = new Stage();

        // 根容器
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
        mediaView.setVisible(false);
        root.getChildren().add(mediaView);

        // 点击图片播放视频
        bg.setOnMouseClicked(e -> playVideo(mediaView));

        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.setTitle("下载任务");
    }

    private void playVideo(MediaView mediaView) {
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
        mediaPlayer.stop();  // 保证可以多次点击播放
        mediaPlayer.play();
        active = true;

        mediaPlayer.setOnEndOfMedia(() -> {
            completeOneStep();
            mediaView.setVisible(false);
        });
    }

    @Override
    public String getTaskName() {
        return taskName;
    }

    @Override
    public void start() {
        if (active || isCompleted()) return;
        active = true;
        stage.show();
    }

    @Override
    public void complete() {
        completedSteps = totalSteps;
        active = false;
        stage.close();
        if (listener != null) listener.onTaskComplete(true);
    }

    @Override
    public void completeOneStep() {
        if (isCompleted()) return;
        completedSteps++;
        active = false; // 每次播放完成后置为非活动
        if (listener != null) listener.onTaskComplete(true);

        if (isCompleted()) {
            stage.close();
        }
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public boolean isCompleted() {
        return completedSteps >= totalSteps;
    }

    @Override
    public int getTotalSteps() {
        return totalSteps;
    }

    @Override
    public int getCompletedSteps() {
        return completedSteps;
    }

    @Override
    public void setTaskCompleteListener(TaskCompleteListener listener) {
        this.listener = listener;
    }

    @Override
    public void setCompletedSteps(int steps) {
        this.completedSteps = steps;
        if (completedSteps >= totalSteps) {
            complete();
        }
    }
}
