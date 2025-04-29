package ui;

import hardware.SerialPortManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.RowConstraints;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class MainWindowController {

    private static final byte PACKET_START = (byte) 0x7E;
    private static final byte PACKET_END = (byte) 0x33;

    private ByteBuffer uartBuffer = ByteBuffer.allocate(1024);
    private volatile boolean running = true;

    @FXML private ComboBox<String> portComboBox;
    @FXML private ComboBox<Integer> baudComboBox;
    @FXML private Button connectButton;

    @FXML private TextField diTextField;
    @FXML private TextField doTextField;
    @FXML private TextField aiTextField;
    @FXML private TextField aoTextField;

    @FXML private VBox diVBox;
    @FXML private VBox doVBox;
    @FXML private VBox aiVBox;
    @FXML private VBox aoVBox;
    @FXML private TextArea logArea;

    private SerialPortManager serialManager = new SerialPortManager();
    private boolean connected = false;

    private List<Label> diLabels = new ArrayList<>();
    private List<XYChart.Series<Number, Number>> aiSeriesList = new ArrayList<>();
    private int time = 0;

    @FXML
    private void initialize() {
        List<String> ports = serialManager.listAvailablePorts();
        portComboBox.getItems().addAll(ports);
        if (!ports.isEmpty()) portComboBox.setValue(ports.get(0));

        baudComboBox.getItems().addAll(9600, 115200, 256000);
        baudComboBox.setValue(115200);

        diTextField.setText("2");
        doTextField.setText("2");
        aiTextField.setText("2");
        aoTextField.setText("1");

        startReadingThread();
    }

    @FXML
    private void onConnectButtonClicked() {
        if (!connected) {
            String port = portComboBox.getValue();
            int baud = baudComboBox.getValue();
            if (serialManager.openPort(port, baud)) {
                connected = true;
                connectButton.setText("Disconnect");
                log("Connected to " + port);
            } else {
                log("Failed to connect.");
            }
        } else {
            serialManager.closePort();
            connected = false;
            connectButton.setText("Connect");
            log("Disconnected.");
        }
    }

    @FXML
    private void onApplyConfigClicked() {
        diVBox.getChildren().clear();
        doVBox.getChildren().clear();
        aiVBox.getChildren().clear();
        aoVBox.getChildren().clear();

        diLabels.clear();
        aiSeriesList.clear();

        int diCount = 0;
        int doCount = 0;
        int aiCount = 0;
        int aoCount = 0;
        try {
            diCount = Integer.parseInt(diTextField.getText());
            doCount = Integer.parseInt(doTextField.getText());
            aiCount = Integer.parseInt(aiTextField.getText());
            aoCount = Integer.parseInt(aoTextField.getText());
            if (diCount < 0 || doCount < 0 || aiCount < 0 || aoCount < 0) {
                log("Counts must be non-negative.");
                return;
            }
        } catch (NumberFormatException e) {
            log("Invalid DI count. Please enter a valid number.");
            return;
        }
        
        // Create DI elements in a matrix layout
        GridPane diGrid = new GridPane();
        diGrid.setHgap(10); 
        diGrid.setVgap(10); 

       
        for (int i = 0; i < 8; i++) { 
            ColumnConstraints colConstraints = new ColumnConstraints();
            colConstraints.setPercentWidth(20); 
            diGrid.getColumnConstraints().add(colConstraints);
        }

       
        for (int i = 0; i < (diCount + 4) / 8; i++) { // Calculate rows based on DI count
            RowConstraints rowConstraints = new RowConstraints();
            rowConstraints.setPrefHeight(50); 
            diGrid.getRowConstraints().add(rowConstraints);
        }

        int columns = 8; 
        for (int i = 0; i < diCount; i++) {
            int row = i / columns; 
            int col = i % columns; 

            VBox diElement = new VBox(8); 
            diElement.setStyle("-fx-alignment: center;");

            Label label = new Label("DI" + i);
            Region box = new Region();
            box.setPrefSize(30, 30);
            box.setMinSize(30, 30);
            box.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE); 
            box.setStyle("-fx-background-color: white; -fx-border-color: black;");

            // Maintain state for each DI
            boolean[] diState = {false}; // false = LOW (white), true = HIGH (red)

            // Simulate DI state change (for demonstration purposes)
            box.setOnMouseClicked(_ -> {
                diState[0] = !diState[0]; 
                if (diState[0]) {
                    box.setStyle("-fx-background-color: red; -fx-border-color: black;");
                } else {
                    box.setStyle("-fx-background-color: white; -fx-border-color: black;");
                }
            });

            diElement.getChildren().addAll(label, box);
            diGrid.add(diElement, col, row); // Add to GridPane at (col, row)
        }

        diVBox.getChildren().add(diGrid); // Add the GridPane to the DI VBox

        // Create DO elements
        GridPane doGrid = new GridPane();
        doGrid.setHgap(10);
        doGrid.setVgap(10);

        for (int i = 0; i < 8; i++) {
            ColumnConstraints colConstraints = new ColumnConstraints();
            colConstraints.setPercentWidth(20);
            doGrid.getColumnConstraints().add(colConstraints);
        }

        for (int i = 0; i < (doCount + 4) / 8; i++) {
            RowConstraints rowConstraints = new RowConstraints();
            rowConstraints.setPrefHeight(50);
            doGrid.getRowConstraints().add(rowConstraints);
        }

        boolean[] doStates = new boolean[doCount];

        for (int i = 0; i < doCount; i++) {
            int row = i / 8;
            int col = i % 8;

            VBox doElement = new VBox(8);
            doElement.setStyle("-fx-alignment: center;");

            Label label = new Label("DO" + i);
            Region box = new Region();
            box.setPrefSize(30, 30);
            box.setMinSize(30, 30);
            box.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            box.setStyle("-fx-background-color: white; -fx-border-color: black;");

            int index = i;
            box.setOnMouseClicked(_ -> {
                doStates[index] = !doStates[index];
                if (doStates[index]) {
                    box.setStyle("-fx-background-color: red; -fx-border-color: black;");
                } else {
                    box.setStyle("-fx-background-color: white; -fx-border-color: black;");
                }
                sendDOState(doStates);
            });

            doElement.getChildren().addAll(label, box);
            doGrid.add(doElement, col, row);
        }

        doVBox.getChildren().add(doGrid);

        // Create AI elements
        for (int i = 0; i < aiCount; i++) {
            VBox aiBox = new VBox(2);
            Label label = new Label("AI" + i + ": 0.00");
            LineChart<Number, Number> chart = createSmallChart();
            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            chart.getData().add(series);
            aiSeriesList.add(series);

            aiBox.getChildren().addAll(label, chart);
            aiVBox.getChildren().add(aiBox);
        }

        // Create AO elements
        for (int i = 0; i < aoCount; i++) {
            HBox hBox = new HBox(5);
            Label label = new Label("AO" + i + ":");
            TextField valueField = new TextField();
            Button sendBtn = new Button("Send");
            hBox.getChildren().addAll(label, valueField, sendBtn);
            aoVBox.getChildren().add(hBox);
        }
    }

    private void startReadingThread() {
        Thread thread = new Thread(() -> {
            while (running) {
                if (serialManager.isOpen()) {
                    byte[] data = serialManager.readData();
                    if (data != null) {
                        handleIncomingData(data);
                    }
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {}
            }
        });
        thread.setDaemon(true);
        thread.start();
    }
    
    private void sendDOState(boolean[] states) {
        int byteCount = (states.length + 7) / 8;
        byte[] packet = new byte[byteCount + 2];
        packet[0] = (byte) 0x7D;
    
        for (int i = 0; i < states.length; i++) {
            int byteIndex = i / 8;
            int bitIndex = i % 8;
            if (states[i]) {
                packet[1 + byteIndex] |= (1 << bitIndex);
            }
        }
    
        packet[packet.length - 1] = (byte) 0x33;
    
        if (serialManager.isOpen()) {
            serialManager.writeData(packet);
        }
    }
    
    private void handleIncomingData(byte[] data) {
        uartBuffer.put(data);

        int startIdx = -1;
        int endIdx = -1;
        byte[] arr = uartBuffer.array();
        int limit = uartBuffer.position();

        for (int i = 0; i < limit; i++) {
            if (arr[i] == PACKET_START && startIdx == -1) {
                startIdx = i;
            } else if (arr[i] == PACKET_END && startIdx != -1) {
                endIdx = i;
                break;
            }
        }

        if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
            int payloadLength = endIdx - startIdx - 1;
            if (payloadLength % 4 == 0) {
                int numFloats = payloadLength / 4;
                float[] floatValues = new float[numFloats];

                ByteBuffer payload = ByteBuffer.wrap(arr, startIdx + 1, payloadLength);
                payload.order(ByteOrder.LITTLE_ENDIAN);

                for (int i = 0; i < numFloats; i++) {
                    floatValues[i] = payload.getFloat();
                }

                Platform.runLater(() -> updateAICharts(floatValues));

                uartBuffer.position(endIdx + 1);
                uartBuffer.compact();
            }
        }

        if (uartBuffer.position() > 512) {
            uartBuffer.clear();
        }
    }

    private void updateAICharts(float[] values) {
        for (int i = 0; i < Math.min(values.length, aiSeriesList.size()); i++) {
            XYChart.Series<Number, Number> series = aiSeriesList.get(i);
            series.getData().add(new XYChart.Data<>(time, values[i]));

            if (series.getData().size() > 30) {
                series.getData().remove(0);
            }
        }
        time++;
    }

    private LineChart<Number, Number> createSmallChart() {
        NumberAxis xAxis = new NumberAxis();
        NumberAxis yAxis = new NumberAxis(0, 4096, 512);
        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setPrefHeight(100);
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        return chart;
    }

    private void log(String message) {
        Platform.runLater(() -> logArea.appendText(message + "\n"));
    }

    public void stop() {
        running = false;
    }
}
