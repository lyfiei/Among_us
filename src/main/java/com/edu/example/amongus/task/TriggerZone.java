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
        zone.setTranslateX(worldX);
        zone.setTranslateY(worldY);
        zone.setFill(Color.YELLOW);
        zone.setOpacity(0.25);
        zone.setStroke(Color.GOLD);
    }

    // 获取矩形视图，加到 Pane 上显示
    public Rectangle getView() {
        return zone;
    }

    // 高亮显示时调用
    public void setHighlighted(boolean highlight) {
        zone.setOpacity(highlight ? 0.6 : 0.25);
        if (highlight) {
            zone.toFront(); // 高亮时置顶
        }
    }

    // 世界坐标检测玩家是否进入触发区
    public boolean isPlayerInside(Player player) {
        double px = player.getX();
        double py = player.getY();
        return px >= worldX && px <= worldX + zoneWidth &&
                py >= worldY && py <= worldY + zoneHeight;
    }


    // Getter
    public String getTaskName() { return taskName; }
    public double getWorldX() { return worldX; }
    public double getWorldY() { return worldY; }
    public double getWidth() { return zoneWidth; }
    public double getHeight() { return zoneHeight; }
}
