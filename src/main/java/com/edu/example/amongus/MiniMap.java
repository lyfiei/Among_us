package com.edu.example.amongus;

import javafx.beans.binding.Bindings;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;

public class MiniMap extends StackPane {
    private final Pane markerPane; // 放玩家标记
    private final ImageView playerIconView; // 玩家小头像
    private final double mapWidth;  // 游戏地图逻辑宽度
    private final double mapHeight; // 游戏地图逻辑高度
    private final double displayWidth;  // 小地图显示宽度
    private final double displayHeight; // 小地图显示高度

    public MiniMap(Image miniMapImage, Image playerIcon,
                   double mapWidth, double mapHeight,
                   double displayWidth, double displayHeight) {
        this.mapWidth = mapWidth;
        this.mapHeight = mapHeight;
        this.displayWidth = displayWidth;
        this.displayHeight = displayHeight;

        // 小地图背景（缩放到 displayWidth / displayHeight）
        ImageView miniMapView = new ImageView(miniMapImage);
        miniMapView.setFitWidth(displayWidth);
        miniMapView.setFitHeight(displayHeight);
        miniMapView.setPreserveRatio(true);

        miniMapView.setOpacity(0.8);

        // 玩家标记层
        markerPane = new Pane();
        markerPane.setPrefSize(displayWidth, displayHeight);
        markerPane.setPickOnBounds(false);

        // 玩家小头像
        playerIconView = new ImageView(playerIcon);
        playerIconView.setFitWidth(300);
        playerIconView.setFitHeight(300);
        markerPane.getChildren().add(playerIconView);

        // 添加到 StackPane
        this.getChildren().addAll(miniMapView, markerPane);
        this.setPrefSize(displayWidth, displayHeight);
    }

    public void updatePlayerPosition(double playerX, double playerY) {
        double miniX = playerX / mapWidth * displayWidth;
        double miniY = playerY / mapHeight * displayHeight;

        playerIconView.setLayoutX(miniX - playerIconView.getFitWidth() / 2);
        playerIconView.setLayoutY(miniY - playerIconView.getFitHeight() / 2);
    }
}
