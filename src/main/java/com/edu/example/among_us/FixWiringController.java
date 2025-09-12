package com.edu.example.among_us;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.stage.Stage;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FixWiringController {

    @FXML private AnchorPane root;
    @FXML
    private ImageView background;

    @FXML private Circle left1, left2, left3, left4;
    @FXML private Circle right1, right2, right3, right4;

    private Line currentLine;
    private Circle currentLeft;

    private FixWiring logic;

    //记录左元和右圆的配对
    private Map<Circle, Circle> connected = new HashMap<>();

    private Circle[] leftCircles;
    private Circle[] rightCircles;

    @FXML
    public void initialize() {
        logic = new FixWiring();

        Image bgImage = new Image(getClass().getResource("/com/edu/example/among_us/image/Fix_Wiring(1).png").toExternalForm());
        background.setImage(bgImage);

        // 直接使用原始图片大小
        background.setFitWidth(bgImage.getWidth());
        background.setFitHeight(bgImage.getHeight());

        // AnchorPane 跟随图片大小
        root.setPrefWidth(bgImage.getWidth());
        root.setPrefHeight(bgImage.getHeight());

////        // 加载背景图
//        Image bgImage = new Image(getClass().getResource("/com/edu/example/among_us/image/Fix_Wiring(1).png").toExternalForm());
//        ImageView background = new ImageView(bgImage);
//
//// 设置大小填满窗口
//        background.fitWidthProperty().bind(root.widthProperty());
//        background.fitHeightProperty().bind(root.heightProperty());
//        background.setPreserveRatio(false);
//
//// 添加到 root，放到最底层
//        root.getChildren().add(0, background);

//        BackgroundImage myBI = new BackgroundImage(
//                new Image(getClass().getResource("/com/edu/example/among_us/image/Fix_Wiring(1).png").toExternalForm(),
//                        0, 0, true, true), // true,true 保持比例
//                BackgroundRepeat.NO_REPEAT,
//                BackgroundRepeat.NO_REPEAT,
//                BackgroundPosition.CENTER,
//                BackgroundSize.DEFAULT);
//
//        root.setBackground(new Background(myBI));



        leftCircles = new Circle[]{left1, left2, left3, left4};
        rightCircles = new Circle[]{right1, right2, right3, right4};

        // 设置右边固定颜色
        Color[] fixedColors = logic.getFixedColors();
        for (int i = 0; i < rightCircles.length; i++) {
            rightCircles[i].setFill(fixedColors[i]);
        }


        //我服了。这时候stage还没渲染，所有给我加载覆盖掉了
        Platform.runLater(() -> {
            List<Color> shuffled = logic.getShuffledColors();
            for (int i = 0; i < leftCircles.length; i++) {
                leftCircles[i].setFill(shuffled.get(i));
            }
        });


        // 给每个左圆绑定拖线事件
        for (Circle left : leftCircles) {
            left.setOnMousePressed(this::startLine);
        }

        // 给根容器绑定拖动事件
        root.setOnMouseDragged(this::dragLine);

        // 给右圆绑定释放事件
        for (Circle right : rightCircles) {
            right.setOnMouseReleased(this::releaseLine);
        }

        root.setOnMouseReleased(event -> releaseLine(event));


    }


    //可以开始拖线事件
    private void startLine(MouseEvent event) {
        currentLeft = (Circle) event.getSource();

        // 如果已经连过线，就直接返回，不允许再拖
        if (connected.containsKey(currentLeft)) {
            System.out.println("这个圆已经连过线了！");
            currentLine = null;
            return;
        }

        currentLine = new Line();
        currentLine.setStartX(currentLeft.getLayoutX());
        currentLine.setStartY(currentLeft.getLayoutY());
        currentLine.setEndX(currentLeft.getLayoutX());
        currentLine.setEndY(currentLeft.getLayoutY());

        // 线条颜色跟随左圆的颜色
        currentLine.setStroke(currentLeft.getFill());
        currentLine.setStrokeWidth(20); // 线条粗细可以调

        root.getChildren().add(currentLine);
    }

    //正在拖线事件
    private void dragLine(MouseEvent event) {
        if (currentLine == null) return;
        currentLine.setEndX(event.getX());
        currentLine.setEndY(event.getY());
    }


    //在空白部分禁止连线,连对了就记录
    private void releaseLine(MouseEvent event) {
        if (currentLine == null || currentLeft == null) return;

        for(Circle rightcircle : rightCircles){
            if (logic.isMouseInCircle(event,rightcircle)) {

                //获取左右圆的序号
                int leftIndex = Arrays.asList(leftCircles).indexOf(currentLeft);
                int rightIndex = Arrays.asList(rightCircles).indexOf(rightcircle);

                if (logic.check(leftIndex, rightIndex)) {
                    // 连对，固定终点
                    System.out.println("逻辑对了");
                    currentLine.setEndX(rightcircle.getLayoutX());
                    currentLine.setEndY(rightcircle.getLayoutY());
                    connected.put(currentLeft, rightcircle);

                    if (connected.size() == leftCircles.length) {
                        System.out.println("任务完成！");
                        Stage stage = (Stage) root.getScene().getWindow();
                        stage.close();   // 关闭窗口
                    }

                    return;
                }
            }
        }
        root.getChildren().remove(currentLine);
        currentLine = null;
        currentLeft = null;

    }
}


