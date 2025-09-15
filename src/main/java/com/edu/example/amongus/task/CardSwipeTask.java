package com.edu.example.amongus.task;

import com.edu.example.amongus.net.NetTaskManager;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;

public class CardSwipeTask implements Task {
    private final Pane root;
    private ImageView card;
    private double startX;
    private long startTime;
    private Pane overlayPane;
    private final String taskName;
    private boolean active = false;
    private final int totalSteps;      // 总步数
    private int completedSteps = 0;    // 已完成步数

    private TaskCompleteListener listener;

    private final NetTaskManager netTaskManager;

    public CardSwipeTask(String taskName,Pane root, int totalSteps, NetTaskManager netTaskManager) {
        this.taskName = taskName;
        this.root = root;
        this.totalSteps = totalSteps;
        this.netTaskManager = netTaskManager;
    }

    @Override
    public String getTaskName() {
        return taskName;
    }

    @Override
    public void setTaskCompleteListener(TaskCompleteListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean isCompleted() {
        return completedSteps >= totalSteps;
    }

    @Override
    public boolean isActive() {
        return active;
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
    public void start() {
        if (active || isCompleted()) return;  // 已激活或已完成就不启动
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
            if (success) {
                netTaskManager.completeOneStep("CardSwipe");
                completedSteps++;
                if (listener != null) listener.onTaskComplete(true);  // ✅ 回调更新状态栏
            }

            card.setX(cardMinX);  // 回到起点
            root.getChildren().remove(overlayPane);
        });
    }

    @Override
    public void complete() {
        completedSteps = totalSteps;
        active = false;
        if (overlayPane != null) root.getChildren().remove(overlayPane);
        if (listener != null) listener.onTaskComplete(true);
    }

    public void completeOneStep() {
        if (isCompleted()) return;
        completedSteps++;
        if (listener != null) listener.onTaskComplete(true);
    }
    @Override
    public void setCompletedSteps(int steps) {
        this.completedSteps = steps;
        if (completedSteps >= totalSteps) {
            complete();
        }
    }

}
