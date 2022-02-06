package jp.ac.utsunomiya_u.is;
 
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.stage.Stage;
 
public class DrawClientTcp extends Application {
 
    // ルートノード
    private final Group root = new Group();
    BorderPane bp=new BorderPane();
    // ソケット
    private Socket socket = null;
    // データ送信用Writer
    private PrintWriter writer = null;
    // データ受信用Reader
    private BufferedReader reader = null;
    // 受信タスク
    private ReceiverTask receiverTask = null;
    
    //描画するペンについての変数
    public static Color drawColor=Color.BLACK;
    public static int drawRadius=2;//描写する太さ
 
    @Override
    public void start(Stage primaryStage) throws Exception {
        // Stageのタイトル
        primaryStage.setTitle(getClass().getName());
        // Stageのサイズ
        primaryStage.setWidth(1200);
        primaryStage.setHeight(800);
        // Stageの終了ボタンが押下された時の対応
        primaryStage.setOnCloseRequest((event) -> {
            exit();
        });
        // Sceneインスタンス生成
        Scene scene = new Scene(root);
        // Scene上でマウスがドラッグされた時の動作
        scene.setOnMouseDragged((event) -> {
            if (socket != null && socket.isConnected()) {
                // マウスの座標から文字列生成
                String str = event.getX() + "," + event.getY()+ "," + drawRadius+","+drawColor;
                for (int i = 0; i < 1000; ++i) {
                    str += "," + event.getX() + "," + event.getY()+ "," + drawRadius+","+drawColor;
                }
                // サーバに情報送信
                writer.println(str);
                // マウス座標を中心とする白い円を描画
                root.getChildren().add(new Circle(event.getX(), event.getY(), 1,Color.WHITE));
            }
        });
        
        // StageにSceneを貼付け
        primaryStage.setScene(scene);
        // IPアドレスとポート番号設定用ペインを作成し，ルートに貼付け        
        //root.getChildren().add(new NetworkConfigurePane());
        
        //root.getChildren().add(new MenuPane());
        
        bp.setTop(new NetworkConfigurePane());
        bp.setLeft(new MenuPane());
        
        root.getChildren().add(bp);
        
        // Stageの表示
        primaryStage.show();
    }
 
    /**
     * ”退室”ボタンが押下された時の処理
     */
    private void exit() {
        // タスクをキャンセル
        receiverTask.cancel();
        try {
            // SocketとReaderとWriterをclose
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
            new Alert(Alert.AlertType.ERROR, "ソケットを閉じることが出来ません", ButtonType.OK).show();
        }
    }
 
    private class NetworkConfigurePane extends GridPane {
 
        //  操作可能コンポーネント
        private final TextField textFieldServerIpAddress = new TextField("localhost");
        private final TextField textFieldServerPortNumber = new TextField("10000");
        private final Button buttonEnter = new Button("入室");
        private final Button buttonExit = new Button("退室");
        
 
        public NetworkConfigurePane() {
            // 各コンポーネントの生成
            Label labelIpAddress = new Label("サーバIPアドレス：");
            Label labelPortNumber = new Label("サーバポート番号：");
            // 各コンポーネントの配置
            GridPane.setConstraints(labelIpAddress, 0, 0);
            GridPane.setConstraints(textFieldServerIpAddress, 1, 0);
            GridPane.setConstraints(labelPortNumber, 0, 1);
            GridPane.setConstraints(textFieldServerPortNumber, 1, 1);
            GridPane.setConstraints(buttonEnter, 2, 1);
            GridPane.setConstraints(buttonExit, 3, 1);
            
            // GridPaneに各コンポーネント追加
            getChildren().addAll(labelIpAddress, textFieldServerIpAddress, labelPortNumber, textFieldServerPortNumber, buttonEnter, buttonExit);
                     
            
            //  コンポーネント表示設定切替
            setConnection(true);
            // 
            buttonEnter.setOnAction((event) -> {
                try {
                    // textFiledServerIpAddressテキストフィールドに入力された文字列からサーバIPアドレスを指定
                    InetAddress serverInetAddress = InetAddress.getByName(textFieldServerIpAddress.getText());
                    // textFieldServerPortNumberテキストフィールドに入力された文字列からサーバポート番号を指定
                    int serverPortNumber = Integer.valueOf(textFieldServerPortNumber.getText());
                    // Socket生成
                    socket = new Socket(serverInetAddress, serverPortNumber);
                    // Socket経由での書込用PrintWriter生成（クライアント->サーバ用）
                    writer = new PrintWriter(socket.getOutputStream(), true);
                    // Soket経由での読込用BufferedReader生成（サーバ->クライアント用）
                    reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    // スレッドプールをSingleThreadで生成
                    ExecutorService executorService = Executors.newSingleThreadExecutor();
                    // TaskReceiver生成
                    receiverTask = new ReceiverTask();
                    // タスク実行
                    executorService.submit(receiverTask);
                    // スレッドプールを停止
                    executorService.shutdown();
                } catch (UnknownHostException ex) {
                    new Alert(Alert.AlertType.ERROR, "IPアドレスが不正です", ButtonType.OK).show();
                } catch (NumberFormatException ex) {
                    new Alert(Alert.AlertType.ERROR, "ポート番号が不正です", ButtonType.OK).show();
                } catch (IOException ex) {
                    new Alert(Alert.AlertType.ERROR, "サーバに接続出来ません", ButtonType.OK).show();
                }
                if (socket != null && socket.isConnected()) {
                    setConnection(false);
                }
            });
            //  ”退室”ボタンが押下された時の処理
            buttonExit.setOnAction((event) -> {
                exit();
                if (socket == null || socket.isClosed()) {
                    setConnection(true);
                }
            });
        }
 
        /**
         * 各コンポーネントの変更可否
         *
         * @param connected 接続時を意味するフラグ
         */
        private void setConnection(boolean connected) {
            textFieldServerIpAddress.setDisable(!connected);
            textFieldServerPortNumber.setDisable(!connected);
            buttonEnter.setDisable(!connected);
            buttonExit.setDisable(connected);
        }
    }
 
    /**
     * サーバからのメッセージを受信するためのタスク
     */
    private class ReceiverTask extends Task<Void> {
 
        @Override
        protected Void call() throws Exception {
            String inputLine;
            while ((inputLine = reader.readLine()) != null) {
                // 受信データを","で分解              
                String[] position = inputLine.split(",");
                
                Platform.runLater(() -> {
                    // 1番目の要素を中心のX座標，2番目の要素を中心のY座標とする円を描画
                    root.getChildren().add(new Circle(Double.valueOf(position[0]), Double.valueOf(position[1]), Integer.valueOf(position[2]), Color.valueOf(position[3])));
                });
            }
            return null;
        }
    }
    
    private class MenuPane extends VBox{
    	
    	List<Button> buttonList=new ArrayList<>();
    	
    	
        public MenuPane() {
        	// ペン(+消しゴム)のリスト
        	//ペンリストの設定    この辺りをすっきりさせたい
        	//名称設定
        	buttonList.add(new Button("黒"));
        	buttonList.add(new Button("赤"));
        	buttonList.add(new Button("オレンジ"));
        	buttonList.add(new Button("黄"));
        	buttonList.add(new Button("緑"));
        	buttonList.add(new Button("青"));
        	buttonList.add(new Button("紫"));
        	buttonList.add(new Button("消しゴム"));
        	
        	buttonList.add(new Button("太"));
        	buttonList.add(new Button("中"));
        	buttonList.add(new Button("細"));
        	
        	
        	//各ボタンの色
        	buttonList.get(0).setBackground(new Background(new BackgroundFill(Color.BLACK,null,null)));
        	buttonList.get(0).setTextFill(Color.WHITE);
        	buttonList.get(1).setBackground(new Background(new BackgroundFill(Color.RED,null,null)));
        	buttonList.get(2).setBackground(new Background(new BackgroundFill(Color.ORANGE,null,null)));
        	buttonList.get(3).setBackground(new Background(new BackgroundFill(Color.YELLOW,null,null)));
        	buttonList.get(4).setBackground(new Background(new BackgroundFill(Color.GREEN,null,null)));
        	buttonList.get(5).setBackground(new Background(new BackgroundFill(Color.BLUE,null,null)));
        	buttonList.get(6).setBackground(new Background(new BackgroundFill(Color.PURPLE,null,null)));
        	buttonList.get(7).setBackground(new Background(new BackgroundFill(Color.WHITE,null,null)));
        	
        	
        	
        	//サイズ
            for(int i=0;i<buttonList.size();i++) {
            	buttonList.get(i).setPrefHeight(40);
            	buttonList.get(i).setPrefWidth(100);
            	buttonList.get(i).setFont(new Font(16));
            }
            VBox vBox = new VBox();
            vBox.setPadding(new Insets(15, 12, 15, 12));
            vBox.setSpacing(10);
            //vBox.setStyle("-fx-background-color: #FFFFFF;");
            
            
            
            getChildren().addAll(buttonList);
        
            buttonList.get(0).setOnAction((event) -> {
                drawColor=Color.BLACK;
            });
            buttonList.get(1).setOnAction((event) -> {
                drawColor=Color.RED;
            });
            buttonList.get(2).setOnAction((event) -> {
                drawColor=Color.ORANGE;
            });
            buttonList.get(3).setOnAction((event) -> {
                drawColor=Color.YELLOW;
            });
            buttonList.get(4).setOnAction((event) -> {
                drawColor=Color.GREEN;
            });
            buttonList.get(5).setOnAction((event) -> {
                drawColor=Color.BLUE;
            });
            buttonList.get(6).setOnAction((event) -> {
                drawColor=Color.PURPLE;
            });
            buttonList.get(7).setOnAction((event) -> {
                drawColor=Color.WHITE;
            });
            buttonList.get(8).setOnAction((event) -> {
                drawRadius=6;
            });
            buttonList.get(9).setOnAction((event) -> {
                drawRadius=2;
            });
            buttonList.get(10).setOnAction((event) -> {
                drawRadius=1;
            });
        }
        
    	
    }
    
    
 
    public static void main(String[] args) {
        launch(args);
    }
}