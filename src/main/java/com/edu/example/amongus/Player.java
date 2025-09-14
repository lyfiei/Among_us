package com.edu.example.amongus;

import javafx.geometry.Bounds;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;

import java.util.List;

public class Player {
    private double x; // 玩家X坐标
    private double y; // 玩家Y坐标
    private final ImageView view; // 玩家视图
    private final PixelReader collisionReader; // 碰撞像素读取器
    private PlayerStatus status = PlayerStatus.ALIVE;

    private static final double KILL_RANGE = 50; // 可调整杀人有效范围
    public enum PlayerType {
        GOOD, EVIL
    }
    private  PlayerType type; // 新增类型字段


    public Player(double startX, double startY, Image playerImage, PixelReader collisionReader, PlayerStatus status) {
        this.x = startX;
        this.y = startY;
        this.view = new ImageView(playerImage);
        this.view.setFitWidth(GameConstants.PLAYER_SIZE);
        this.view.setFitHeight(GameConstants.PLAYER_SIZE);
        this.collisionReader = collisionReader;
        this.status = status;
        this.type = null;
        updateView();
    }

    public Player(double startX, double startY, Image playerImage, PixelReader collisionReader) {
        this.x = startX;
        this.y = startY;
        this.view = new ImageView(playerImage);
        this.view.setFitWidth(GameConstants.PLAYER_SIZE);
        this.view.setFitHeight(GameConstants.PLAYER_SIZE);
        this.collisionReader = collisionReader;
        this.type = null;
        updateView();
    }

    public PlayerType getType() {
        return type;
    }

    public void setType(PlayerType type) {
        this.type = type; // GOOD / EVIL
        // 可以根据 type 改变玩家外观或标识
    }

    public ImageView getView() { return view; }
    public double getX() { return x; }
    public double getY() { return y; }
    public PlayerStatus getStatus() { return status; }

    public void setStatus(PlayerStatus status) {
        this.status = status;
        if (status == PlayerStatus.DEAD) {
            view.setOpacity(0.4);
        } else {
            view.setOpacity(1.0);
        }
    }

    public void setPosition(double newX, double newY) {
        this.x = newX;
        this.y = newY;
        updateView();
    }
    /**
     * 坏人杀附近的玩家
     * @param allPlayers 当前场上所有玩家列表
     * @return 被淘汰的玩家，如果没有则返回 null
     */
    public Player killNearbyPlayer(List<Player> allPlayers) {
        if (this.type != PlayerType.EVIL) return null; // 只有坏人能杀
        if (!this.isAlive()) return null; // 死了不能杀

        for (Player target : allPlayers) {
            if (target == this) continue;          // 跳过自己
            if (!target.isAlive()) continue;       // 跳过已死亡玩家
            if (target.getType() == PlayerType.EVIL) continue; // 跳过其他坏人

            double distance = Math.hypot(this.x - target.getX(), this.y - target.getY());
            if (distance <= KILL_RANGE) {
                target.setStatus(PlayerStatus.DEAD);
                System.out.println("Player " + target + " has been killed by " + this);
                // TODO: 这里可以发送网络消息同步给其他客户端
                return target; // 只杀第一个符合条件的玩家
            }
        }
        return null; // 没有玩家被杀
    }

    public boolean isAlive() {
        return status == PlayerStatus.ALIVE;
    }

    public void move(double dx, double dy) {
        // 如果已经被淘汰，move 不应该被调用（caller 应负责），但这里再双保险不做移动
        if (!isAlive()) return;

        double newX = x + dx;
        double newY = y + dy;

        if (isValidPosition(newX, newY)) {
            x = newX;
            y = newY;
            updateView();

            if (dx > 0) view.setScaleX(-1);
            else if (dx < 0) view.setScaleX(1);
        }
    }

    private void updateView() {
        view.setX(x);
        view.setY(y);
    }

    private boolean isValidPosition(double x, double y) {
        int steps = 3;
        int threshold = 200;
        int offsetY = 2;

        for (int i = 0; i <= steps; i++) {
            int checkX = (int)(x + i * GameConstants.PLAYER_SIZE / steps);
            int checkY = (int)(y + GameConstants.PLAYER_SIZE - offsetY);

            if (checkX < 0 || checkX >= GameConstants.MAP_WIDTH ||
                    checkY < 0 || checkY >= GameConstants.MAP_HEIGHT) return false;

            int argb = collisionReader.getArgb(checkX, checkY);
            int r = (argb >> 16) & 0xFF;
            int g = (argb >> 8) & 0xFF;
            int b = argb & 0xFF;

            if (r < threshold || g < threshold || b < threshold) return false;
        }
        return true;
    }

    public Bounds getBounds() { return view.getBoundsInParent(); }
}
