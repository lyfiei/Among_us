package com.edu.example.amongus.task;

import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import com.edu.example.amongus.Player;

public class TriggerZone {
    private final Rectangle zone;  // 触发区域
    private final String taskName; // 绑定的任务名，比如 "CardSwipe"
    private final double worldX, worldY; // 区域左上角坐标
    private final double zoneWidth, zoneHeight; // 区域大小

    public TriggerZone(double x, double y, double width, double height, String taskName) {
        // 必须在这里初始化所有 final 变量
        this.worldX = x;
        this.worldY = y;
        this.zoneWidth = width;
        this.zoneHeight = height;
        this.taskName = taskName;

        // 创建矩形，只设置大小，位置用 translate
        zone = new Rectangle(zoneWidth, zoneHeight);
        zone.setFill(Color.color(1, 1, 0, 0.25)); // 半透明黄色
        zone.setStroke(Color.GOLD);
        zone.setOpacity(0.25);
    }


    // ----------------- 高亮 -----------------
    public void setHighlighted(boolean highlight) {
        zone.setOpacity(highlight ? 0.6 : 0.25);
        if (highlight) zone.toFront(); // 高亮时放到最上层
    }
    // ----------------- 玩家检测 -----------------
    public boolean isPlayerInside(Player player) {
        double px = player.getX();
        double py = player.getY();
        return px >= worldX && px <= worldX + zoneWidth &&
                py >= worldY && py <= worldY + zoneHeight;
    }


    // ----------------- Getter -----------------
    public Rectangle getView() { return zone; }
    public double getWorldX() { return worldX; }
    public double getWorldY() { return worldY; }
    public String getTaskName() { return taskName; }

    // ----------------- 更新显示位置（随地图滚动） -----------------
    public void updatePosition(double offsetX, double offsetY) {
        zone.setX(worldX + offsetX);
        zone.setY(worldY + offsetY);
    }
}
