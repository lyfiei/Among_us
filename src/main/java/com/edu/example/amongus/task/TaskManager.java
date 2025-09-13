package com.edu.example.amongus.task;

import com.edu.example.amongus.Player;
import javafx.scene.layout.Pane;

import java.util.ArrayList;
import java.util.List;

public class TaskManager {
    private final List<Task> tasks = new ArrayList<>();              // 管理任务
    private final List<TriggerZone> zones = new ArrayList<>();       // 管理触发区
    private final Pane gamePane;                                     // 用来显示黄色矩形

    // 当前玩家所在（最近检测到）的触发区索引，-1 表示不在任何区
    private int currentZoneIndex = -1;

    public TaskManager(Pane gamePane) {
        this.gamePane = gamePane;
    }

    // 绑定任务与触发区，并把可视矩形加入 Pane（调试可见）
    public void addTask(Task task, TriggerZone zone) {
        tasks.add(task);
        zones.add(zone);
        // 把 zone 的 view 放在地图上（注意图层顺序，如果需要把 trigger 放在地图上方，就 add 到合适位置）
        gamePane.getChildren().add(zone.getView());
        zone.getView().toFront();
    }

    public void checkTasks(Player player) {
        currentZoneIndex = -1;
        for (int i = 0; i < tasks.size(); i++) {
            Task t = tasks.get(i);
            TriggerZone z = zones.get(i);

            // 先重置高亮
            z.setHighlighted(false);

            if (t.isCompleted() || t.isActive()) continue;

            if (z.isPlayerInside(player)) {
                currentZoneIndex = i;
                z.setHighlighted(true);          // 高亮矩形
                z.getView().toFront();           // 置顶显示
                System.out.println("已到达指定任务区域: " + z.getTaskName());
                break; // 找到一个触发区就行
            }
        }
    }


    // 由外部按键调用：如果玩家在当前 zone 且名字匹配，就启动对应任务
    public void tryStartTask(String taskName) {
        if (currentZoneIndex < 0) return;
        TriggerZone z = zones.get(currentZoneIndex);
        Task t = tasks.get(currentZoneIndex);
        if (z.getTaskName().equals(taskName) && !t.isActive() && !t.isCompleted()) {
            System.out.println("按键确认，启动任务: " + taskName);
            t.start();
            // 启动后取消高亮
            z.setHighlighted(false);
            currentZoneIndex = -1;
        }
    }

    // 判断所有任务是否完成
    public boolean allCompleted() {
        for (Task task : tasks) {
            if (!task.isCompleted()) return false;
        }
        return true;
    }

    public int getCurrentZoneIndex() {
        return currentZoneIndex;
    }

    public List<TriggerZone> getZones() {
        return zones;
    }

}
