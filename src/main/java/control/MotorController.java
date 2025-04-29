package control;

import hardware.SerialPortManager;

public class MotorController implements Runnable {

    private SerialPortManager serial;

    public MotorController(SerialPortManager serial) {
        this.serial = serial;
    }

    @Override
    public void run() {
        System.out.println("Motor Controller started.");
        while (true) {
            // Gửi lệnh giả lập PWM speed mỗi 100ms
            byte[] command = new byte[] { (byte)0xA5, 0x01, 0x32 }; // Ví dụ: 50% duty cycle
            serial.sendData(command);

            try {
                Thread.sleep(100); // Chu kỳ 100ms
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
