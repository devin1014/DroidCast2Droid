package com.devin.android.java_client

import java.net.URI

internal object JavaClient {
    @JvmStatic
    fun main(args: Array<String>) {
        try {
            // 2. 创建客户端实例
            val client = MyWebSocketClient(URI("ws://192.168.5.126:8080/stream"))

            // 3. 设置额外的 HTTP Header (可选，如认证 Token)
            // client.addHeader("Authorization", "Bearer your-token");

            // 4. 发起连接 (异步非阻塞)
            client.connect();

            // 5. 等待连接建立 (实际生产中建议使用 CountDownLatch 或回调机制，而非 sleep)
            // 这里仅为了演示，让主线程存活一段时间以接收消息
            Thread.sleep(5000)

            // 6. 发送消息
            if (client.isOpen) {
                client.send("Hello Server!");
                System.out.println("Message sent.");
            }

            // 7. 保持运行以接收服务端推送
            Thread.sleep(30_000);

            // 8. 关闭连接
            client.close();
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
