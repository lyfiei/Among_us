package com.edu.example.amongus.net;

import com.edu.example.amongus.task.Task;
import com.edu.example.amongus.task.TaskManager;
import com.edu.example.amongus.task.TaskStatus;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class NetTaskManager {
    private final TaskManager taskManager;
    private final GameClient client;

    public NetTaskManager(TaskManager taskManager, GameClient client) {
        this.taskManager = taskManager;
        this.client = client;
    }

    /**
     * 玩家尝试完成任务一步
     * 1. 检查是否在触发区
     * 2. 检查任务是否已全局完成
     * 3. 通过服务器广播更新全局状态
     */
    public void completeOneStep(String taskName) {
        Task task = taskManager.getTask(taskName);
        if (task == null) return;

        // 已全局完成，直接返回
        if (task.isCompleted()) return;

        // 玩家不在触发区
        if (!taskManager.isInZone(taskName, taskManager.getPlayer())) {
            System.out.println("无法完成任务 " + taskName + "，玩家不在指定区域");
            return;
        }

        // 广播给服务器，让其他玩家同步
        Map<String, String> payload = new HashMap<>();
        payload.put("taskName", taskName);
        payload.put("completedSteps", String.valueOf(task.getCompletedSteps() + 1)); // 本地完成 +1
        try {
            System.out.println("[CLIENT-SEND] TASK_UPDATE -> "
                    + taskName + " steps=" + (task.getCompletedSteps() + 1));

            if (client != null) client.send("TASK_UPDATE", payload);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 收到服务器广播，更新本地任务状态
     */
    public void onNetworkUpdate(String taskName, int completedSteps) {
        Task task = taskManager.getTask(taskName);
        if (task == null) return;

        // 更新本地 Task 步数
        task.setCompletedSteps(completedSteps);
        // 如果完成，触发 Task 内部完成逻辑
        if (task.getCompletedSteps() >= task.getTotalSteps()) {
            task.complete();
        }

        // 刷新状态栏
        TaskStatus status = taskManager.getStatusBar().getStatusByName(taskName);
        if (status != null) {
            status.setCompleted(completedSteps);
            taskManager.getStatusBar().updateTask(status);
        }
    }
}
