package com.edu.example.amongus;

import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;

public class CardSwipeTask implements Task {  // ✅ 实现 Task 接口
    private final Pane root;
    private ImageView card;
    private double startX;
    private long startTime;
    private Pane overlayPane;

    private boolean completed = false;
    private boolean active = false;

    private TaskCompleteListener listener;

    public CardSwipeTask(Pane root) {
        this.root = root;
    }

    @Override
    public void setTaskCompleteListener(TaskCompleteListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean isCompleted() {
        return completed;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void start() {
        if (active || completed) return;  // 任务已激活或完成就不启动
        active = true;

        overlayPane = new Pane();
        overlayPane.setPrefSize(root.getWidth(), root.getHeight());

        Image machineImg = new Image(getClass().getResource("/com/edu/example/amongus/images/card_machine.png").toExternalForm());
        ImageView machine = new ImageView(machineImg);
        machine.setFitWidth(600);
        machine.setPreserveRatio(true);
        machine.setX(50);
        machine.setY(50);

        Image coverImg = new Image(getClass().getResource("/com/edu/example/amongus/images/card_cover.png").toExternalForm());
        ImageView frontCover = new ImageView(coverImg);
        frontCover.setFitWidth(machine.getFitWidth());
        frontCover.setPreserveRatio(true);
        frontCover.setX(machine.getX());
        frontCover.setY(machine.getY());

        Image cardImg = new Image(getClass().getResource("/com/edu/example/amongus/images/card.png").toExternalForm());
        card = new ImageView(cardImg);
        card.setFitWidth(200);
        card.setFitHeight(120);
        card.setPreserveRatio(true);
        card.setX(machine.getX() + 50);
        card.setY(200);

        Label resultLabel = new Label("");
        resultLabel.setLayoutX(50);
        resultLabel.setLayoutY(700);

        overlayPane.getChildren().addAll(machine, card, frontCover, resultLabel);
        root.getChildren().add(overlayPane);

        double cardMinX = machine.getX() + 20;
        double cardMaxX = machine.getX() + machine.getFitWidth() - card.getFitWidth() - 20;

        card.setOnMousePressed(e -> {
            startX = e.getSceneX();
            startTime = System.currentTimeMillis();
        });

        card.setOnMouseDragged(e -> {
            double newX = e.getSceneX() - card.getFitWidth() / 2;
            if (newX >= cardMinX && newX <= cardMaxX) card.setX(newX);
        });

        card.setOnMouseReleased(e -> {
            double endX = e.getSceneX();
            long endTime = System.currentTimeMillis();
            double distance = endX - startX;
            long time = endTime - startTime;
            double speed = distance / time;

            boolean success = distance > 150 && speed > 0.3 && speed < 1.5;
            resultLabel.setText(success ? "✅ 刷卡成功！" : "❌ 刷卡失败");

            active = false;
            completed = success;  // 更新完成状态

            if (listener != null) listener.onTaskComplete(success); // ✅ 回调给 TaskManager

            card.setX(cardMinX);  // 回到起点
            root.getChildren().remove(overlayPane);
        });
    }

    @Override
    public void complete() { // 可以被 TaskManager 手动完成
        active = false;
        completed = true;
        if (overlayPane != null) root.getChildren().remove(overlayPane);
        if (listener != null) listener.onTaskComplete(true);
    }
}
