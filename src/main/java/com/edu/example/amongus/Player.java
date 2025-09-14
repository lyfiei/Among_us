package com.edu.example.amongus;

import javafx.geometry.Bounds;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;

public class Player {
    private double x;// 玩家X坐标
    private double y;
    private final ImageView view;// 玩家视图
    private final PixelReader collisionReader;// 地图碰撞像素读取器

    public Player(double startX, double startY, Image playerImage, PixelReader collisionReader) {
        this.x = startX;
        this.y = startY;
        this.view = new ImageView(playerImage);
        this.view.setFitWidth(GameConstants.PLAYER_SIZE);
        this.view.setFitHeight(GameConstants.PLAYER_SIZE);
        this.collisionReader = collisionReader;
        updateView();
    }

    public ImageView getView() {
        return view;
    }

    public double getX() { return x; }
    public double getY() { return y; }

    public void move(double dx, double dy) {
        double newX = x + dx;
        double newY = y + dy;

        // 碰撞检测
        if (isValidPosition(newX, newY)) {
            x = newX;
            y = newY;
            updateView();

            // 朝向控制：水平翻转
            if (dx > 0) {
                view.setScaleX(-1); // 向右 → 翻转
            } else if (dx < 0) {
                view.setScaleX(1);  // 向左 → 正常
            }

        }
    }

    private void updateView() {
        view.setX(x);
        view.setY(y);
    }

    // 脚底多点碰撞检测（更宽松）
    private boolean isValidPosition(double x, double y) {
        int steps = 3; // 底部检测点数量
        int threshold = 200; // 颜色阈值，越小越宽松
        int offsetY = 2; // 脚底往上收缩，避免卡住

        for (int i = 0; i <= steps; i++) {
            int checkX = (int)(x + i * GameConstants.PLAYER_SIZE / steps);
            int checkY = (int)(y + GameConstants.PLAYER_SIZE - offsetY);

            // 越界就不能走
            if (checkX < 0 || checkX >= GameConstants.MAP_WIDTH ||
                    checkY < 0 || checkY >= GameConstants.MAP_HEIGHT) {
                return false;
            }

            int argb = collisionReader.getArgb(checkX, checkY);
            int r = (argb >> 16) & 0xFF;
            int g = (argb >> 8) & 0xFF;
            int b = argb & 0xFF;

            if (r < threshold || g < threshold || b < threshold) return false;
        }
        return true;
    }

    public Bounds getBounds() {
        return view.getBoundsInParent(); // view 是 ImageView
    }


}
