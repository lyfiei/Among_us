package com.edu.example.amongus;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;

public class Player {
    private double x;
    private double y;
    private final ImageView view;
    private final PixelReader collisionReader;

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

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public void move(double dx, double dy) {
        double newX = x + dx;
        double newY = y + dy;
        if (isValidPosition(newX, newY)) {
            x = newX;
            y = newY;
            updateView();
        }
    }

    private void updateView() {
        view.setX(x);
        view.setY(y);
    }

    // 脚底碰撞检测
    private boolean isValidPosition(double x, double y) {
        int steps = 5; // 检测底部一条线的几个点
        int threshold = 240;
        for (int i = 0; i <= steps; i++) {
            int checkX = (int)(x + i * GameConstants.PLAYER_SIZE / steps);
            int checkY = (int)(y + GameConstants.PLAYER_SIZE);
            if (checkX < 0 || checkX >= GameConstants.MAP_WIDTH || checkY < 0 || checkY >= GameConstants.MAP_HEIGHT) {
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
}

