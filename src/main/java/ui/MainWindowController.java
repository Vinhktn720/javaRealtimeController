package ui;

import hardware.SerialPortManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.Node;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class MainWindowController {

    @FXML private TextField serialPortCountField;
    @FXML private VBox serialPortConfigVBox;
    @FXML private TabPane tabPane;

    private SerialPortManager serialManager = new SerialPortManager();

    // Store config controls for each port
    private static class SerialPortConfig {
        ComboBox<String> portComboBox;
        ComboBox<Integer> baudComboBox;
        TextField diTextField, doTextField, aiTextField, aoTextField;
        Button applyButton;
        Tab controllerTab;
        VBox diVBox, doVBox, aiVBox, aoVBox;
        TextArea logArea;
        List<Label> diLabels = new ArrayList<>();
        List<Label> aiLabels = new ArrayList<>();
        List<XYChart.Series<Number, Number>> aiSeriesList = new ArrayList<>();
        List<AIChartConfig> aiChartConfigs = new ArrayList<>();
        int time = 0;
        boolean connected = false;
    }
    private static class AIChartConfig {
        int timeWindow = 30;
        String color = "#3366cc";
        double pointSize = 3.0;
    }
    private final List<SerialPortConfig> serialPortConfigs = new ArrayList<>();

    private volatile boolean running = true;
    private static final int UART_BUFFER_SIZE = 1024;
    private final ByteBuffer uartBuffer = ByteBuffer.allocate(UART_BUFFER_SIZE);
    private static final byte PACKET_START = 0x7E;
    private static final byte PACKET_END = 0x33;

    private final List<ScheduledFuture<?>> aoAutoTasks = new ArrayList<>();
    private final ScheduledExecutorService aoScheduler = Executors.newScheduledThreadPool(2);

    @FXML
    private void initialize() {
        serialPortCountField.setText("1");
        startReadingThread();
    }

    @FXML
    private void onSetSerialPortCount() {
        serialPortConfigVBox.getChildren().clear();
        serialPortConfigs.clear();
        int count;
        try {
            count = Integer.parseInt(serialPortCountField.getText());
            if (count < 1) return;
        } catch (NumberFormatException e) {
            return;
        }
    
        List<String> ports = serialManager.listAvailablePorts();
    
        for (int i = 0; i < count; i++) {
            SerialPortConfig config = new SerialPortConfig();
    
            // --- Top row: Port and Baudrate
            HBox hBoxTop = new HBox(10);
            config.portComboBox = new ComboBox<>();
            config.portComboBox.getItems().addAll(ports);
            if (!ports.isEmpty()) config.portComboBox.setValue(ports.get(0));
    
            config.baudComboBox = new ComboBox<>();
            config.baudComboBox.getItems().addAll(9600, 115200, 256000);
            config.baudComboBox.setValue(115200);
    
            HBox.setHgrow(config.portComboBox, Priority.ALWAYS);
            HBox.setHgrow(config.baudComboBox, Priority.ALWAYS);
    
            hBoxTop.getChildren().addAll(
                new Label("Port:"), config.portComboBox,
                new Label("Baud:"), config.baudComboBox
            );
    
            // --- GridPane for DI/DO/AI/AO and Apply button
            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(5);
            grid.setPadding(new Insets(5));
    
            Label diLabel = new Label("DI:");
            Label doLabel = new Label("DO:");
            Label aiLabel = new Label("AI:");
            Label aoLabel = new Label("AO:");
    
            config.diTextField = new TextField("2");
            config.doTextField = new TextField("2");
            config.aiTextField = new TextField("2");
            config.aoTextField = new TextField("1");
    
            config.applyButton = new Button("Apply Config");
    
            int portIndex = i;
            config.applyButton.setOnAction(e -> {
                SerialPortConfig thisConfig = serialPortConfigs.get(portIndex);
                if (!thisConfig.connected) {
                    String portName = thisConfig.portComboBox.getValue();
                    int baud = thisConfig.baudComboBox.getValue();
                    boolean connected = serialManager.openPort(portName, baud);
                    if (connected) {
                        thisConfig.connected = true;
                        thisConfig.applyButton.setText("Disconnect");
                        applySerialPortConfig(portIndex);
                        log("Connected to " + portName + " at " + baud + " baud.");
                    } else {
                        log("Failed to connect to " + portName);
                    }
                } else {
                    serialManager.closePort();
                    thisConfig.connected = false;
                    thisConfig.applyButton.setText("Apply Config");
                    log("Disconnected from port.");
                    if (thisConfig.controllerTab != null) {
                        tabPane.getTabs().remove(thisConfig.controllerTab);
                        thisConfig.controllerTab = null;
                    }
                }
            });
    
            config.diTextField.setMaxWidth(Double.MAX_VALUE);
            config.doTextField.setMaxWidth(Double.MAX_VALUE);
            config.aiTextField.setMaxWidth(Double.MAX_VALUE);
            config.aoTextField.setMaxWidth(Double.MAX_VALUE);
            GridPane.setHgrow(config.diTextField, Priority.ALWAYS);
            GridPane.setHgrow(config.doTextField, Priority.ALWAYS);
            GridPane.setHgrow(config.aiTextField, Priority.ALWAYS);
            GridPane.setHgrow(config.aoTextField, Priority.ALWAYS);
    
            grid.addRow(0, diLabel, config.diTextField, doLabel, config.doTextField);
            grid.addRow(1, aiLabel, config.aiTextField, aoLabel, config.aoTextField);
            grid.add(config.applyButton, 0, 2, 4, 1); // Spans across columns
    
            // Optional: fixed label width
            double labelWidth = 30;
            diLabel.setMinWidth(labelWidth);
            doLabel.setMinWidth(labelWidth);
            aiLabel.setMinWidth(labelWidth);
            aoLabel.setMinWidth(labelWidth);
    
            // Combine both rows
            VBox configBox = new VBox(10, hBoxTop, grid);
            configBox.setPadding(new Insets(10));
            configBox.setStyle("-fx-border-color: gray; -fx-border-width: 1; -fx-border-radius: 5;");
            configBox.setMaxWidth(Double.MAX_VALUE);
            hBoxTop.setMaxWidth(Double.MAX_VALUE);
            grid.setMaxWidth(Double.MAX_VALUE);
            VBox.setVgrow(configBox, Priority.ALWAYS);
    
            serialPortConfigVBox.getChildren().add(configBox);
            serialPortConfigs.add(config);
        }
    }
    

    private void applySerialPortConfig(int index) {
        SerialPortConfig config = serialPortConfigs.get(index);

        // Remove old tab if exists
        if (config.controllerTab != null) {
            tabPane.getTabs().remove(config.controllerTab);
        }

        // Create new controller tab for this serial port
        VBox controllerContent = new VBox(10);
        controllerContent.setPadding(new Insets(10));

        // DI/DO/AI/AO VBoxes for this port
        config.diVBox = new VBox(5);
        config.doVBox = new VBox(5);
        config.aiVBox = new VBox(5);
        config.aoVBox = new VBox(5);
        config.logArea = new TextArea();
        config.logArea.setPrefHeight(100);

        // Add sections
        controllerContent.getChildren().addAll(
            new Label("Controller for " + config.portComboBox.getValue()),
            titledPane("Digital Inputs", config.diVBox),
            titledPane("Digital Outputs", config.doVBox),
            titledPane("Analog Inputs", config.aiVBox),
            titledPane("Analog Outputs", config.aoVBox),
            new Label("Log:"),
            config.logArea
        );

        config.controllerTab = new Tab("Controller " + (index + 1));
        config.controllerTab.setContent(new ScrollPane(controllerContent));
        tabPane.getTabs().add(config.controllerTab);

        // Build DI/DO/AI/AO UI for this port
        buildPortUI(config);
    }

    private TitledPane titledPane(String title, VBox content) {
        TitledPane pane = new TitledPane(title, content);
        pane.setExpanded(true);
        return pane;
    }

    private void buildPortUI(SerialPortConfig config) {
        config.diVBox.getChildren().clear();
        config.doVBox.getChildren().clear();
        config.aiVBox.getChildren().clear();
        config.aoVBox.getChildren().clear();
        config.diLabels.clear();
        config.aiSeriesList.clear();

        int diCount = parseInt(config.diTextField.getText(), 0);
        int doCount = parseInt(config.doTextField.getText(), 0);
        int aiCount = parseInt(config.aiTextField.getText(), 0);
        int aoCount = parseInt(config.aoTextField.getText(), 0);

        // DI
        GridPane diGrid = new GridPane();
        diGrid.setHgap(10);
        diGrid.setVgap(10);
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

            boolean[] diState = {false};
            box.setOnMouseClicked(_ -> {
                diState[0] = !diState[0];
                if (diState[0]) {
                    box.setStyle("-fx-background-color: red; -fx-border-color: black;");
                } else {
                    box.setStyle("-fx-background-color: white; -fx-border-color: black;");
                }
            });

            diElement.getChildren().addAll(label, box);
            diGrid.add(diElement, col, row);
        }
        config.diVBox.getChildren().add(diGrid);

        // DO
        GridPane doGrid = new GridPane();
        doGrid.setHgap(10);
        doGrid.setVgap(10);
        boolean[] doStates = new boolean[doCount];
        for (int i = 0; i < doCount; i++) {
            int row = i / columns;
            int col = i % columns;

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
                sendDOState(doStates); // You can implement per-port sending here
            });

            doElement.getChildren().addAll(label, box);
            doGrid.add(doElement, col, row);
        }
        config.doVBox.getChildren().add(doGrid);

        // AI
        config.aiVBox.getChildren().clear();
        config.aiLabels = new ArrayList<>();
        config.aiSeriesList.clear();

        GridPane aiGrid = new GridPane();
        aiGrid.setHgap(10);
        aiGrid.setVgap(10);
        int aiColumns = 1;
        for (int i = 0; i < aiCount; i++) {
            int row = i / aiColumns;
            int col = i % aiColumns;

            VBox aiElement = new VBox(5);
            aiElement.setStyle("-fx-alignment: center;");

            Label label = new Label("AI" + i);
            Label valueLabel = new Label("0.00");
            valueLabel.setStyle("-fx-border-color: gray; -fx-padding: 4; -fx-min-width: 50; -fx-alignment: center;");

            // Chart config controls
            AIChartConfig chartConfig = new AIChartConfig();
            TextField timeWindowField = new TextField(String.valueOf(chartConfig.timeWindow));
            timeWindowField.setPrefWidth(40);
            ColorPicker colorPicker = new ColorPicker(javafx.scene.paint.Color.web(chartConfig.color));
            colorPicker.setPrefWidth(40);
            Slider pointSizeSlider = new Slider(1, 10, chartConfig.pointSize);
            pointSizeSlider.setPrefWidth(60);
            pointSizeSlider.setShowTickLabels(true);
            pointSizeSlider.setShowTickMarks(true);
            pointSizeSlider.setMajorTickUnit(3);

            HBox configBox = new HBox(8, new Label("Window:"), timeWindowField, new Label("Color:"), colorPicker, new Label("Point:"), pointSizeSlider);
            configBox.setStyle("-fx-alignment: center-left;");

            // Line chart for this AI
            LineChart<Number, Number> chart = createSmallChart();
            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            chart.getData().add(series);
            config.aiSeriesList.add(series);

            // Apply config changes
            timeWindowField.textProperty().addListener((obs, oldVal, newVal) -> {
                try {
                    int v = Integer.parseInt(newVal);
                    chartConfig.timeWindow = Math.max(1, v);
                } catch (NumberFormatException ignored) {}
            });
            colorPicker.valueProperty().addListener((obs, oldVal, newVal) -> {
                chartConfig.color = toRgbString(newVal);
                setSeriesLineColor(series, chartConfig.color);
            });
            pointSizeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                chartConfig.pointSize = newVal.doubleValue();
                setSeriesPointSize(series, chartConfig.pointSize);
            });

            // Initial style
            setSeriesLineColor(series, chartConfig.color);
            setSeriesPointSize(series, chartConfig.pointSize);

            // Store chartConfig for updateAICharts
            if (config.aiChartConfigs == null) config.aiChartConfigs = new ArrayList<>();
            config.aiChartConfigs.add(chartConfig);

            aiElement.getChildren().addAll(label, valueLabel, configBox, chart);
            aiGrid.add(aiElement, col, row);
            config.aiLabels.add(valueLabel);
        }
        config.aiVBox.getChildren().add(aiGrid);

        // AO
        
        for (int i = 0; i < aoCount; i++) {
            final int index = i;
        
            Label label = new Label("AO" + i + ":");
        
            ComboBox<String> modeBox = new ComboBox<>();
            modeBox.getItems().addAll("Raw Value", "Sine Wave", "Square Wave");
            modeBox.setValue("Raw Value");
        
            // --- Controls
            Label valueLabel = new Label("Value:");
            TextField valueField = new TextField("0.0");
        
            Label freqLabel = new Label("Freq (Hz):");
            TextField freqField = new TextField("1.0");
        
            Label ampLabel = new Label("Amplitude:");
            TextField ampField = new TextField("1.0");
        
            Label highLabel = new Label("High:");
            TextField highField = new TextField("1.0");
        
            Label lowLabel = new Label("Low:");
            TextField lowField = new TextField("0.0");
        
            Button publishBtn = new Button("Publish");
            ToggleButton autoBtn = new ToggleButton("Auto");
        
            // --- Visibility handling
            valueLabel.setVisible(true); valueField.setVisible(true);
            freqLabel.setVisible(false); freqField.setVisible(false);
            ampLabel.setVisible(false); ampField.setVisible(false);
            highLabel.setVisible(false); highField.setVisible(false);
            lowLabel.setVisible(false); lowField.setVisible(false);
        
            modeBox.valueProperty().addListener((obs, oldVal, newVal) -> {
                boolean isRaw = "Raw Value".equals(newVal);
                boolean isSine = "Sine Wave".equals(newVal);
                boolean isSquare = "Square Wave".equals(newVal);
        
                valueLabel.setVisible(isRaw); valueField.setVisible(isRaw);
                freqLabel.setVisible(isSine || isSquare); freqField.setVisible(isSine || isSquare);
                ampLabel.setVisible(isSine); ampField.setVisible(isSine);
                highLabel.setVisible(isSquare); highField.setVisible(isSquare);
                lowLabel.setVisible(isSquare); lowField.setVisible(isSquare);
            });
        
            // --- Layout
            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(5);
            grid.setPadding(new Insets(10));
            ColumnConstraints col1 = new ColumnConstraints();
            col1.setPercentWidth(15);
            ColumnConstraints col2 = new ColumnConstraints();
            col2.setPercentWidth(35);
            grid.getColumnConstraints().addAll(col1, col2, col1, col2);
        
            // First row: mode and buttons
            grid.add(label, 0, 0);
            grid.add(modeBox, 1, 0);
            grid.add(autoBtn, 2, 0);
            grid.add(publishBtn, 3, 0);
        
            grid.addRow(1, valueLabel, valueField);
            grid.addRow(2, freqLabel, freqField);
            grid.addRow(3, ampLabel, ampField);
            grid.addRow(4, highLabel, highField, lowLabel, lowField);
        
            // Wrap in a bordered VBox
            VBox aoBox = new VBox(grid);
            aoBox.setPadding(new Insets(5));
            aoBox.setStyle("-fx-border-color: gray; -fx-border-width: 1; -fx-border-radius: 4;");
            aoBox.setMaxWidth(Double.MAX_VALUE);
        
            config.aoVBox.getChildren().add(aoBox);
        
            // --- Manual publish
            publishBtn.setOnAction(e -> {
                sendAOValue(modeBox, valueField, freqField, ampField, highField, lowField, config, index);
            });
        
            // --- Auto publish
            if (aoAutoTasks.size() <= index) {
                aoAutoTasks.add(null);
            }
            autoBtn.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
                ScheduledFuture<?> prevTask = aoAutoTasks.get(index);
                if (prevTask != null && !prevTask.isCancelled()) {
                    prevTask.cancel(true);
                }
                if (isSelected) {
                    ScheduledFuture<?> task = aoScheduler.scheduleAtFixedRate(() -> {
                        Platform.runLater(() -> sendAOValue(modeBox, valueField, freqField, ampField, highField, lowField, config, index));
                    }, 0, 50, TimeUnit.MILLISECONDS); // 20Hz
                    aoAutoTasks.set(index, task);
                }
            });
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
        for (SerialPortConfig config : serialPortConfigs) {
            for (int i = 0; i < Math.min(values.length, config.aiSeriesList.size()); i++) {
                XYChart.Series<Number, Number> series = config.aiSeriesList.get(i);
                AIChartConfig chartConfig = config.aiChartConfigs.get(i);
                int now = config.time;
                int window = chartConfig.timeWindow;

                // Add new data point
                series.getData().add(new XYChart.Data<>(now, values[i]));

                // Remove points outside the window
                while (!series.getData().isEmpty() && ((int)series.getData().get(0).getXValue()) < now - window) {
                    series.getData().remove(0);
                }

                // Update label
                if (i < config.aiLabels.size()) {
                    config.aiLabels.get(i).setText(String.format("%.2f", values[i]));
                }

                // Update style
                setSeriesLineColor(series, chartConfig.color);
                setSeriesPointSize(series, chartConfig.pointSize);

                // Auto-scale Y axis for visible points
                LineChart<Number, Number> chart = (LineChart<Number, Number>) series.getChart();
                autoScaleYAxis(chart, series);
                // Set X axis window
                NumberAxis xAxis = (NumberAxis) chart.getXAxis();
                xAxis.setAutoRanging(false);
                xAxis.setLowerBound(now - window);
                xAxis.setUpperBound(now);
            }
            config.time++;
        }
    }

    private void autoScaleYAxis(LineChart<Number, Number> chart, XYChart.Series<Number, Number> series) {
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (XYChart.Data<Number, Number> d : series.getData()) {
            double v = d.getYValue().doubleValue();
            if (v < min) min = v;
            if (v > max) max = v;
        }
        if (min == Double.MAX_VALUE || max == -Double.MAX_VALUE) {
            min = 0; max = 1;
        }
        NumberAxis yAxis = (NumberAxis) chart.getYAxis();
        yAxis.setAutoRanging(false);
        yAxis.setLowerBound(min - 1);
        yAxis.setUpperBound(max + 1);
    }

    private LineChart<Number, Number> createSmallChart() {
        NumberAxis xAxis = new NumberAxis();
        NumberAxis yAxis = new NumberAxis(0, 4096, 512);
        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setPrefHeight(100);
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setCreateSymbols(true); // Show points
        return chart;
    }

    private void log(String message) {
        Platform.runLater(() -> {
            Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
            if (selectedTab != null) {
                for (SerialPortConfig config : serialPortConfigs) {
                    if (config.controllerTab == selectedTab && config.logArea != null) {
                        config.logArea.appendText(message + "\n");
                        return;
                    }
                }
            }
            // fallback: log to first config if no tab selected
            if (!serialPortConfigs.isEmpty() && serialPortConfigs.get(0).logArea != null) {
                serialPortConfigs.get(0).logArea.appendText(message + "\n");
            }
        });
    }

    public void stop() {
        running = false;
    }

    private int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return def;
        }
    }

    private String toRgbString(javafx.scene.paint.Color c) {
        return String.format("#%02x%02x%02x",
            (int)(c.getRed()*255),
            (int)(c.getGreen()*255),
            (int)(c.getBlue()*255));
    }

    private void setSeriesLineColor(XYChart.Series<Number, Number> series, String color) {
        Platform.runLater(() -> {
            Node line = series.getNode().lookup(".chart-series-line");
            if (line != null) line.setStyle("-fx-stroke: " + color + ";");
        });
    }

    private void setSeriesPointSize(XYChart.Series<Number, Number> series, double size) {
        Platform.runLater(() -> {
            for (XYChart.Data<Number, Number> d : series.getData()) {
                Node n = d.getNode();
                if (n != null) n.setStyle("-fx-background-radius: " + size + "; -fx-padding: 0; -fx-background-color: #ffffff, #000000;");
            }
        });
    }

    private void sendAnalogOutput(float output, SerialPortConfig config) {
        byte[] frame = new byte[6];
        frame[0] = 0x7E;
        int intBits = Float.floatToIntBits(output);
        frame[1] = (byte)(intBits & 0xFF);
        frame[2] = (byte)((intBits >> 8) & 0xFF);
        frame[3] = (byte)((intBits >> 16) & 0xFF);
        frame[4] = (byte)((intBits >> 24) & 0xFF);
        frame[5] = 0x33;
        // Send using your SerialPortManager
        serialManager.sendData(frame);
        log("AO sent: " + output);
    }

    private void sendAOValue(
        ComboBox<String> modeBox,
        TextField valueField,
        TextField freqField,
        TextField ampField,
        TextField highField,
        TextField lowField,
        SerialPortConfig config,
        int index
    ) {
        String mode = modeBox.getValue();
        float output = 0f;
        if ("Raw Value".equals(mode)) {
            try {
                output = Float.parseFloat(valueField.getText());
            } catch (NumberFormatException ex) {
                log("Invalid raw value for AO" + index);
                return;
            }
        } else if ("Sine Wave".equals(mode)) {
            try {
                float freq = Float.parseFloat(freqField.getText());
                float amp = Float.parseFloat(ampField.getText());
                long now = System.currentTimeMillis();
                output = (float)(amp * Math.sin(2 * Math.PI * freq * now / 1000.0));
            } catch (NumberFormatException ex) {
                log("Invalid sine config for AO" + index);
                return;
            }
        } else if ("Square Wave".equals(mode)) {
            try {
                float freq = Float.parseFloat(freqField.getText());
                float high = Float.parseFloat(highField.getText());
                float low = Float.parseFloat(lowField.getText());
                long now = System.currentTimeMillis();
                double period = 1000.0 / freq;
                output = ((now % period) < (period / 2)) ? high : low;
            } catch (NumberFormatException ex) {
                log("Invalid square config for AO" + index);
                return;
            }
        }
        sendAnalogOutput(output, config);
    }
}
