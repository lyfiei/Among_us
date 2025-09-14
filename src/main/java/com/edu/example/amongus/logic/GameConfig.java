package com.edu.example.amongus.logic;

import com.edu.example.amongus.net.GameClient;
import com.edu.example.amongus.net.Message;
import com.edu.example.amongus.ui.MatchUpdateListener;
import javafx.application.Platform;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


public class GameConfig {
    private static String playerId;  // 唯一 ID
    private static String playerName;
    private static String playerColor;
    private static String playerRole; // GOOD / EVIL
    private static boolean joined = false;

    private static GameClient client;




    // ====== 本地配置 ======

    public static String getPlayerId() {
        return playerId;
    }

    public static void setPlayerId(String id) {
        playerId = id;
    }

    public static String getPlayerName() {
        return playerName;
    }

    public static void setPlayerName(String name) {
        playerName = name;
    }

    public static String getPlayerColor() {
        return playerColor;
    }

    public static void setPlayerColor(String color) {
        playerColor = color;
    }

    public static String getPlayerRole() {
        return playerRole;
    }

    public static void setPlayerRole(String role) {
        playerRole = role;
    }

    public static boolean isJoined() {
        return joined;
    }

    public static void setJoined(boolean value) {
        joined = value;
    }


    // ====== 网络相关 ======
    public static void initNetwork(String host, int port) throws IOException {
        client = new GameClient(host, port, GameConfig::handleServerMessage);
    }


    public static void handleServerMessage(Message.Parsed parsed) {
        if (parsed == null) return;
        switch (parsed.type) {
            case "ROLE":
                String role = parsed.payload.get("type");
                System.out.println("你的角色是: " + role);
                setPlayerRole(role);
                break;
            case "GAME_START":
                System.out.println("游戏开始！");
                // 收到服务器 GAME_START 后启动游戏
                Platform.runLater(() -> {
                    System.out.println("收到服务器 GAME_START 后启动游戏runLater 执行了");
                    //System.out.println("primaryStage = " + primaryStage);
                    com.edu.example.amongus.Main.startGameScene();
                });
                break;
        }
    }
    public static GameClient getClient() {
        return client;
    }
}

