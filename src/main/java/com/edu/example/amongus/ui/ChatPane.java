package com.edu.example.amongus.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import java.util.function.Consumer;

public class ChatPane extends Pane {
    private final VBox messageList = new VBox(8);
    private final ScrollPane scroll = new ScrollPane(messageList);
    private final TextField input = new TextField();
    private final Button sendBtn = new Button("发送");
    private final Consumer<String> onSend;
    private final String myNick;
    private final String myColor;

    public ChatPane(Consumer<String> onSend, String myNick, String myColor) {
        this.onSend = onSend;
        this.myNick = myNick;
        this.myColor = myColor;

        // layout
        scroll.setPrefSize(300, 240);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setFitToWidth(true);

        input.setPromptText("输入消息，回车或点击发送");
        input.setPrefWidth(220);
        sendBtn.setPrefWidth(60);

        HBox inputBox = new HBox(8, input, sendBtn);
        inputBox.setPadding(new Insets(6));

        VBox root = new VBox(6, scroll, inputBox);
        root.setPadding(new Insets(8));
        root.setPrefSize(300, 300);

        // 背景+圆角
        root.setStyle("-fx-background-color: rgba(0,0,0,0.6); -fx-background-radius: 8;");

        this.getChildren().add(root);

        // 默认隐藏
        this.setVisible(false);
        this.setManaged(false);

        // 事件
        sendBtn.setOnAction(e -> sendCurrent());
        input.setOnAction(e -> sendCurrent());
    }

    private void sendCurrent() {
        String text = input.getText().trim();
        if (text.isEmpty()) return;

        if (onSend != null) {
            try {
                onSend.accept(text);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        input.clear();
    }

    public void addMessage(String nick, String color, String msg, boolean isSelf) {
        Platform.runLater(() -> {
            HBox row = new HBox(8);
            row.setPadding(new Insets(4));

            // 头像
            ImageView avatar = new ImageView();
            try {
                Image img = new Image(getClass().getResourceAsStream(
                        "/com/edu/example/amongus/images/" + color + ".png"));
                avatar.setImage(img);
                avatar.setFitWidth(28);
                avatar.setFitHeight(28);
            } catch (Exception ignored) { }

            // 昵称标签
            Label nameLabel = new Label(nick);
            nameLabel.setStyle("-fx-font-size: 12; -fx-font-weight: bold; -fx-text-fill: black;");

            // 消息内容气泡
            Label msgLabel = new Label(msg);
            msgLabel.setWrapText(true);
            msgLabel.setMaxWidth(220);
            if (isSelf) {
                msgLabel.setStyle("-fx-background-color: #00aaff; -fx-text-fill: white; -fx-padding:6; -fx-background-radius:6;");
            } else {
                msgLabel.setStyle("-fx-background-color: #eeeeee; -fx-text-fill: black; -fx-padding:6; -fx-background-radius:6;");
            }

            VBox bubble = new VBox(2, nameLabel, msgLabel);

            if (isSelf) {
                row.setAlignment(Pos.CENTER_RIGHT);
                row.getChildren().addAll(bubble, avatar);
            } else {
                row.setAlignment(Pos.CENTER_LEFT);
                row.getChildren().addAll(avatar, bubble);
            }

            messageList.getChildren().add(row);

            // 滚动到底
            scroll.setVvalue(1.0);
        });
    }


    // 新增方法：让外部调用时聚焦输入框
    public void requestFocusInput() {
        Platform.runLater(() -> {
            input.requestFocus();   // 聚焦输入框
            input.positionCaret(input.getText().length()); // 光标置于文本末尾
        });
    }


    public void show() {
        setVisible(true);
        setManaged(true);
        requestFocusInput(); // 打开时自动聚焦
    }

    public void hide() {
        setVisible(false);
        setManaged(false);
    }

    public void toggle() {
        if (isVisible()) hide(); else show();
    }
}
