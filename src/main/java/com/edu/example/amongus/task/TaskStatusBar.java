package com.edu.example.amongus.task;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

public class TaskStatusBar extends HBox {
    private final ObservableList<TaskStatus> taskList = FXCollections.observableArrayList();

    public TaskStatusBar() {
        setSpacing(20);
        setStyle("-fx-background-color: rgba(200,200,200,0.5); -fx-padding: 10;");
        setMinWidth(200);
        setMinHeight(50);
    }

    public void addTask(TaskStatus task) {
        taskList.add(task);
        updateUI();
        System.out.println("Task added: " + task.getClass().getSimpleName());
    }

    public void updateTask(TaskStatus task) {
        updateUI();
    }

    private void updateUI() {
        getChildren().clear();
        for (TaskStatus task : taskList) {
            Label label = new Label(task.getStatusText());
            label.setStyle("-fx-text-fill: white; -fx-font-size: 14px;");
            getChildren().add(label);
        }
    }
    public TaskStatus getStatusByName(String taskName) {
        for (TaskStatus task : taskList) {
            if (task.getTaskName().equals(taskName)) {  // 精确匹配，不依赖 statusText
                return task;
            }
        }
        return null;
    }

}

