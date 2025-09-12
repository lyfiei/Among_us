package com.edu.example.amongus;

public interface Task {
    void start();                     // 开始任务
    void complete();                  // 手动完成任务
    boolean isActive();               // 是否正在进行
    boolean isCompleted();            // 是否完成

    void setTaskCompleteListener(TaskCompleteListener listener);

    interface TaskCompleteListener {
        void onTaskComplete(boolean success);
    }
}
