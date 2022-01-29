package jp.ac.utsunomiya_u.is;
 
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
 
public class DrawServerTcp {
 
    // DrawTaskのリスト
    private final ArrayList<DrawTask> drawTasks = new ArrayList<>();
 
    public static void main(String[] args) {
        DrawServerTcp drawServer = new DrawServerTcp();
    }
 
    public DrawServerTcp() {
        // Scannerクラスのインスタンス（標準入力System.inからの入力）
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.print("DrawServerTcp (" + getMyIpAddress() + ") > Input server port > ");
            // ポート番号入力
            int port = scanner.nextInt();
            // スレッドプールの生成
            ExecutorService executorService = Executors.newCachedThreadPool();
            // ServerSocketクラスのインスタンスをポート番号を指定して生成
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                System.out.println("DrawServerTcp (" + getMyIpAddress() + ") > Started and Listening for connections on port " + serverSocket.getLocalPort());
                while (true) {
                    // ServerSocketに対する要求を待機し，それを受け取ったらSocketクラスのインスタンスからChatTaskを生成
                    DrawTask drawTask = new DrawTask(serverSocket.accept());
                    // ChatTaskのインスタンスをリストに保管
                    drawTasks.add(drawTask);
                    // タスクの実行
                    executorService.submit(drawTask);
                }
            } catch (IOException ex) {
                Logger.getLogger(DrawServerTcp.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                // スレッドプールの停止
                executorService.shutdown();
            }
        }
    }
 
    /**
     * 自ホストのIPアドレス取得
     *
     * @return 自ホストのIPアドレス
     */
    private static String getMyIpAddress() {
        try {
            // 自ホストのIPアドレス取得
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException ex) {
            Logger.getLogger(DrawServerTcp.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
 
    /**
     * メッセージの同報通知（サーバ->クライアント）
     *
     * @param message メッセージ
     */
    private synchronized void broadcast(String message) {
        // ChatTashのArrayListの各要素に対して
        drawTasks.forEach((drawTask) -> {
            // PrintWriter経由でメッセージ送信
            drawTask.getPrintWriter().println(message);
        });
    }
 
    private final class DrawTask implements Callable<Void> {
 
        // ソケット
        private Socket socket;
        // データ送信用Writer（サーバ->クライアント用）
        private PrintWriter writer = null;
        // データ受信用Reader（クライアント->サーバ用）
        private BufferedReader reader = null;
 
        /**
         * コンストラクタ
         *
         * @param socket Socketクラスのインスタンス
         */
        DrawTask(Socket socket) {
            this.socket = socket;
            try {
                // Socket経由での書込用PrintWriter生成（サーバ->クライアント用）
                writer = new PrintWriter(socket.getOutputStream(), true);
                // Soket経由での読込用BufferedReader生成（クライアント->サーバ用）
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                System.out.println("DrawServerTcp (" + getMyIpAddress() + ") > Accepted connection from " + socket.getRemoteSocketAddress());
            } catch (IOException ex) {
                Logger.getLogger(DrawServerTcp.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
 
        /**
         * PrintWriterのゲッタ
         *
         * @return PrintWriter
         */
        public PrintWriter getPrintWriter() {
            return writer;
        }
 
        @Override
        public Void call() {
            try {
                String inputLine;
                // readerから一行読み込み
                while ((inputLine = reader.readLine()) != null) {
                    // 受信文をそのまま同報通知
                    broadcast(inputLine);
                    //System.out.println("DrawClientTcp (" + socket.getRemoteSocketAddress() + ") > " + inputLine);
                }
            } catch (IOException ex) {
                Logger.getLogger(DrawServerTcp.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                System.out.println("DrawServerTcp (" + getMyIpAddress() + ") > Terminated connection from " + socket.getRemoteSocketAddress());
                try {
                    // socket, reader, writerのclose
                    if (socket != null) {
                        socket.close();
                    }
                    if (reader != null) {
                        reader.close();
                    }
                    if (writer != null) {
                        writer.close();
                    }
                } catch (IOException ex) {
                    Logger.getLogger(DrawServerTcp.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            // ChatTaskのリストから自身を削除         
            drawTasks.remove(this);
            return null;
        }
    }
}