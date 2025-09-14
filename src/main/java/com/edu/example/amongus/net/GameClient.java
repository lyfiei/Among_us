package com.edu.example.amongus.net;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 简单客户端：连接服务器，发送消息，监听服务器广播。
 * onMessage 会在 listener 线程中被调用（UI 层应自行 Platform.runLater 包装）。
 */
public class GameClient {
    private final Socket socket;
    private final BufferedWriter out;
    private final BufferedReader in;
    private final Thread listenerThread;
    private final Consumer<Message.Parsed> onMessage;

    public GameClient(String host, int port, Consumer<Message.Parsed> onMessage) throws IOException {
        this.socket = new Socket(host, port);
        this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.onMessage = onMessage;

        listenerThread = new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    System.out.println("收到原始消息: " + line); // ✅ 打印服务器发来的每条消息
                    Message.Parsed p = Message.parse(line);
                    System.out.println("准备调用 onMessage.accept");
                    try {
                        onMessage.accept(p);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            } catch (IOException e) {
                System.out.println("Disconnected from server: " + e.getMessage());
            }
        }, "ClientListener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public synchronized void send(String type, Map<String, String> payload) throws IOException {
        String raw = Message.build(type, payload);
        out.write(raw);
        out.write("\n");
        out.flush();
    }

    public void close() {
        try { socket.close(); } catch (IOException ignored) {}
    }
}
