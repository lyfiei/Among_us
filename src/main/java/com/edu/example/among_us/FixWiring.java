package com.edu.example.among_us;

import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FixWiring {
        private boolean completed = false;
        private List<Integer>index =Arrays.asList(0,1,2,3);
        //游戏开始
        public void start() {

            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com.edu.example.among_us/fixWiring.fxml"));
                Scene scene = new Scene(loader.load());
                Stage stage = new Stage();
                stage.setTitle("Fix Wiring");
                stage.setScene(scene);
                stage.show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        //判断游戏是否结束
        public boolean isCompleted() {
            return completed;
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
//        System.out.println("index: " + index);
//        System.out.println("leftColors: " + leftColors);
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
