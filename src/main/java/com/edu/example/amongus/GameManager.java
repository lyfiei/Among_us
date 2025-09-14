package com.edu.example.amongus;

import java.util.List;

public class GameManager {
    private List<Player> players; // 全部玩家
    private boolean sabotageActive = false; // 是否有坏人破坏
    private long sabotageDeadline = 0;      // 破坏倒计时的结束时间戳

    public GameManager(List<Player> players) {
        this.players = players;
    }

    // 投票结束后调用，淘汰一个玩家
    public void eliminatePlayer(Player target) {
        target.setStatus(PlayerStatus.DEAD);
        checkGameOver();
    }

    // 坏人发起破坏
    public void startSabotage(long durationMs) {
        sabotageActive = true;
        sabotageDeadline = System.currentTimeMillis() + durationMs;
    }

    // 好人修复破坏
    public void fixSabotage() {
        sabotageActive = false;
    }

    // 在游戏循环里定时调用
    public void update() {
        if (sabotageActive && System.currentTimeMillis() > sabotageDeadline) {
            // 时间到了，坏人直接获胜
            endGame("坏人破坏成功，坏人胜利！");
        }
    }

    // 检查游戏是否结束
    public void checkGameOver() {
        long evilCount = players.stream()
                .filter(p -> p.getType() == Player.PlayerType.EVIL && p.isAlive())
                .count();
        long goodCount = players.stream()
                .filter(p -> p.getType() == Player.PlayerType.GOOD && p.isAlive())
                .count();

        if (evilCount == 0) {
            endGame("坏人全部出局，好人胜利！");
        } else if (evilCount >= goodCount) {
            endGame("坏人数量 ≥ 好人数量，坏人胜利！");
        }
    }

    // 游戏结束
    private void endGame(String message) {
        System.out.println("游戏结束：" + message);
        // TODO: 可以加上 UI 界面提示，返回大厅等
    }
}
