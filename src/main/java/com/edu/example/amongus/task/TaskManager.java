package com.edu.example.amongus.task;

import com.edu.example.amongus.Player;
import javafx.scene.layout.Pane;

import java.util.ArrayList;
import java.util.List;

public class TaskManager {
    private final List<Task> tasks = new ArrayList<>();              // 管理任务
    private final List<TriggerZone> zones = new ArrayList<>();       // 管理触发区
    private final Pane gamePane;                                     // 用来显示黄色矩形
    private Player player;

    // 当前玩家所在（最近检测到）的触发区索引，-1 表示不在任何区
    private int currentZoneIndex = -1;

    private final TaskStatusBar statusBar; // 任务状态栏

    public TaskManager(Pane gamePane, TaskStatusBar statusBar) {
        this.gamePane = gamePane;
        this.statusBar = statusBar;
    }


    // 绑定任务与触发区，并把可视矩形加入 Pane（调试可见）
    public void addTask(Task task, TriggerZone zone) {
        tasks.add(task);
        zones.add(zone);
        // 把 zone 的 view 放在地图上（注意图层顺序，如果需要把 trigger 放在地图上方，就 add 到合适位置）
        gamePane.getChildren().add(zone.getView());
        zone.getView().toFront();

        // 注册任务状态
        TaskStatus status = new TaskStatus(zone.getTaskName(), task.getTotalSteps(), task.getCompletedSteps());

        // 给任务注册监听，每完成一步就更新状态栏
        task.setTaskCompleteListener(success -> {
            if (success) {
                status.completeOne();
                statusBar.updateTask(status);
            }
        });
        statusBar.addTask(status);

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

    // 判断玩家是否在某个任务的触发区
    public boolean isInZone(String taskName, Player player) {
        for (int i = 0; i < tasks.size(); i++) {
            Task t = tasks.get(i);
            TriggerZone z = zones.get(i);
            if (t.getClass().getSimpleName().equals(taskName) && z.isPlayerInside(player)) {
                return true;
            }
        }
        return false;
    }

    // 在指定任务名完成一步任务，并更新状态栏（供 NetTaskManager 调用）
    public void completeOneStep(String taskName) {
        Task t = getTask(taskName);
        if (t == null) return;

        t.completeOneStep();

        // 更新对应状态栏
        TaskStatus status = statusBar.getStatusByName(taskName);
        if (status != null) {
            status.setCompleted(t.getCompletedSteps());
            statusBar.updateTask(status);
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

    public Task getTask(String taskName) {
        for (Task task : tasks) {
            if (task.getClass().getSimpleName().equals(taskName)) {
                return task;
            }
        }
        return null;
    }

    public void updateStatusBar() {
        for (Task task : tasks) {
            TaskStatus status = statusBar.getStatusByName(task.getClass().getSimpleName());
            if (status != null) {
                status.setCompleted(task.getCompletedSteps());
                statusBar.updateTask(status);
            }
        }
    }
    public void setPlayer(Player player) { this.player = player; }
    public Player getPlayer() { return this.player; }

    public TaskStatusBar getStatusBar() {
        return statusBar;
    }

}
