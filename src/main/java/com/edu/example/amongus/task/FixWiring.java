package com.edu.example.amongus.task;

import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FixWiring implements Task{
        private boolean active = false;
        private boolean completed = false;
        private TaskCompleteListener listener;
        private List<Integer>index =Arrays.asList(0,1,2,3);

    public void start() {
        if (completed || active) return;
        active = true;

        System.out.println("修电线任务开始！");

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/edu/example/amongus/fixWiring.fxml")
            );
            Parent root = loader.load();

            // 拿到控制器，传入逻辑对象
            FixWiringController controller = loader.getController();
            controller.initData(this);

            // 弹出一个新窗口（模态对话框，阻塞主游戏）
            Stage stage = new Stage();
            stage.setTitle("修电线任务");
            stage.setScene(new Scene(root,800,600));

            stage.sizeToScene();
            stage.initModality(Modality.APPLICATION_MODAL); // 阻塞父窗口
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void complete() {
        if (!active || completed) return;
        active = false;
        completed = true;

        System.out.println("修电线任务完成 ✅");
        if (listener != null) {
            listener.onTaskComplete(true); // 通知 TaskManager 或游戏主逻辑
        }
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


    private final Color[] fixedColors = {
            Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW
    };

    public Color[] getFixedColors() {
        return fixedColors;
    }

    public List<Color> getShuffledColors() {
        //左右映射打乱关系
        Collections.shuffle(index);

        List<Color> colors = Arrays.asList(fixedColors);
        List<Color> leftColors = new ArrayList<>();
        for (int i = 0; i < index.size(); i++) {
            leftColors.add(colors.get(index.get(i)));
        }
        return leftColors;
    }

    // 判定连线是否正确
    public boolean check(int leftIndex, int rightIndex) {
        // 左边编号映射到右边正确编号
        return index.get(leftIndex) == rightIndex;
    }

    //判断鼠标是否在圆内
    public boolean isMouseInCircle(MouseEvent event, Circle circle) {
        double mouseX = event.getSceneX(); // 或 getX() / getSceneX() 看情况
        double mouseY = event.getSceneY();

        // 圆心相对于 Scene 的位置
        Bounds bounds = circle.localToScene(circle.getBoundsInLocal());
        double centerX = bounds.getMinX() + circle.getRadius();
        double centerY = bounds.getMinY() + circle.getRadius();

        double dx = mouseX - centerX;
        double dy = mouseY - centerY;

        return dx*dx + dy*dy <= circle.getRadius() * circle.getRadius();
    }

}
