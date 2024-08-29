package test;

import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.CountDownLatch;

import org.java_websocket.WebSocket.READYSTATE;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/**
 * 实时转写调用demo
 * 此demo只是一个简单的调用示例，不适合用到实际生产环境中
 *
 * @author whites
 *
 */
public class astMain {

    private static final String HOST = "ws://172.20.0.242:30051/tuling/ast/v2/ASTXVBVOPT3D5JBF?appId=1111&bizId=123&bizName=WebSocket&lan=chin&sr=16000&bps=16&fs=1280";

    // 音频文件路径
    private static final String AUDIO_PATH = "ast.pcm";

    // 每次发送的数据大小 1280 字节
    private static final int CHUNCKED_SIZE = 1280;

    private static final CountDownLatch connectClose = new CountDownLatch(1);

    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyy-MM-dd HH:mm:ss.SSS");

    public static void main(String[] args) throws Exception {

        URI url = new URI(HOST);
        MyWebSocketClient client = new MyWebSocketClient(url);

        client.connect();

        while (!client.getReadyState().equals(READYSTATE.OPEN)) {
            System.out.println(getCurrentTimeStr() + "\t连接中");
            Thread.sleep(1000);
        }


        System.out.println(sdf.format(new Date()) + " 开始发送音频数据");
        // 发送音频
        byte[] bytes = new byte[CHUNCKED_SIZE];

        try (RandomAccessFile raf = new RandomAccessFile(AUDIO_PATH, "r")) {
            int len;
            while ((len = raf.read(bytes)) != -1) {
                if (len < CHUNCKED_SIZE) {
                    send(client, Arrays.copyOfRange(bytes, 0, len));
                    break;
                }

                send(client, bytes);
                // 每隔40毫秒发送一次数据
                Thread.sleep(40);
            }

            // 发送结束标识
            send(client,"".getBytes());
           System.out.println(getCurrentTimeStr() + "\t发送结束标识完成");
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 等待连接关闭
        connectClose.await();

    }


    public static void send(WebSocketClient client, byte[] bytes) {
        if (client.isClosed()) {
            throw new RuntimeException("client connect closed!");
        }
        client.send(bytes);
    }

    public static String getCurrentTimeStr() {
        return sdf.format(new Date());
    }

    public static class MyWebSocketClient extends WebSocketClient {

        public MyWebSocketClient(URI serverUri) {
            super(serverUri);
        }
        @Override
        public void onOpen(ServerHandshake handshake) {
            System.out.println(getCurrentTimeStr() + "\t连接建立成功！");
        }

        @Override
        public void onMessage( String msg) {
            // 转写结果
            String resultdata = getContent(msg);
            if (resultdata.length()>0) {
                System.out.println(getCurrentTimeStr() + "\tresult: " + resultdata);
            }
        }

        @Override
        public void onError(Exception e) {
            System.out.println(getCurrentTimeStr() + "\t连接发生错误：" + e.getMessage() + ", " + new Date());
            e.printStackTrace();
            System.exit(0);
        }

        @Override
        public void onClose(int arg0, String arg1, boolean arg2) {
            System.out.println(getCurrentTimeStr() + "\t链接关闭");
            connectClose.countDown();
        }

        @Override
        public void onMessage(ByteBuffer bytes) {
            System.out.println(getCurrentTimeStr() + "\t服务端返回：" + new String(bytes.array(), StandardCharsets.UTF_8));
        }
    }

    // 把转写结果解析为句子
    public static String getContent(String message) {
        StringBuilder resultBuilder = new StringBuilder();
        try {
            JSONObject messageObj = JSON.parseObject(message);

            //取最终结果
             if(messageObj.getString("msgtype").equals("sentence")) {

                JSONArray wsArr = messageObj.getJSONArray("ws");
                for (int j = 0; j < wsArr.size(); j++) {
                    JSONObject wsArrObj = wsArr.getJSONObject(j);
                    JSONArray cwArr = wsArrObj.getJSONArray("cw");
                    for (int k = 0; k < cwArr.size(); k++) {
                        JSONObject cwArrObj = cwArr.getJSONObject(k);
                        String wStr = cwArrObj.getString("w");
                        resultBuilder.append(wStr);
                    }
                }
              }
        } catch (Exception e) {
            return "";
        }

        return resultBuilder.toString();
    }
}