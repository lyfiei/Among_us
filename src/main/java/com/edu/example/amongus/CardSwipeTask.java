package com.edu.example.amongus;

import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;

public class CardSwipeTask {
    private final Pane root;
    private ImageView card;
    private double startX;
    private long startTime;
    private Pane overlayPane;

    // 回调接口
    public interface TaskCompleteListener {
        void onComplete(boolean success);
    }
    private TaskCompleteListener listener;

    public CardSwipeTask(Pane root) {
        this.root = root;
    }

    public void setTaskCompleteListener(TaskCompleteListener listener) {
        this.listener = listener;
    }

    public void start() {
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

            // 回调
            if (listener != null) listener.onComplete(success);

            // 回到起点
            card.setX(cardMinX);

            // 移除覆盖层
            root.getChildren().remove(overlayPane);
        });

        root.getChildren().add(overlayPane);
    }
}
