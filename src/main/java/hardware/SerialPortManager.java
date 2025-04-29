package hardware;

import com.fazecast.jSerialComm.SerialPort;
import java.util.ArrayList;
import java.util.List;

public class SerialPortManager {

    private SerialPort comPort;

    public List<String> listAvailablePorts() {
        SerialPort[] ports = SerialPort.getCommPorts();
        List<String> portNames = new ArrayList<>();
        for (SerialPort port : ports) {
            portNames.add(port.getSystemPortName());
        }
        return portNames;
    }

    public boolean openPort(String portName, int baudRate) {
        comPort = SerialPort.getCommPort(portName);
        comPort.setBaudRate(baudRate);
        comPort.setNumDataBits(8);
        comPort.setNumStopBits(SerialPort.ONE_STOP_BIT);
        comPort.setParity(SerialPort.NO_PARITY);
        return comPort.openPort();
    }

    public void sendData(byte[] data) {
        if (comPort != null && comPort.isOpen()) {
            comPort.writeBytes(data, data.length);
        }
    }

    public boolean writeData(byte[] data) {
        if (comPort == null || !comPort.isOpen()) return false;
        return comPort.writeBytes(data, data.length) > 0;
    }
    public byte[] readData() {
        if (comPort != null && comPort.isOpen()) {
            byte[] readBuffer = new byte[1024];
            int numBytes = comPort.readBytes(readBuffer, readBuffer.length);
            if (numBytes > 0) {
                byte[] actualData = new byte[numBytes];
                System.arraycopy(readBuffer, 0, actualData, 0, numBytes);
                return actualData;
            }
        }
        return null;
    }

    public void closePort() {
        if (comPort != null) {
            comPort.closePort();
        }
    }

    public boolean isOpen() {
        return comPort != null && comPort.isOpen();
    }
}
