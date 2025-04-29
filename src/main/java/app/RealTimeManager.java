package app;

import hardware.SerialPortManager;
import control.MotorController;

public class RealTimeManager {

    private Thread motorControlThread;

    public void startScheduler(SerialPortManager serial) {
        System.out.println("Starting Scheduler...");

        MotorController motorController = new MotorController(serial);

        motorControlThread = new Thread(motorController);
        motorControlThread.setPriority(Thread.NORM_PRIORITY + 2); // Ưu tiên cao hơn mặc định
        motorControlThread.start();
    }
}
