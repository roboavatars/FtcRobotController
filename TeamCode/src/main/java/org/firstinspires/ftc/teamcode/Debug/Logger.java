package org.firstinspires.ftc.teamcode.Debug;

import android.annotation.SuppressLint;

import org.firstinspires.ftc.teamcode.RobotClasses.Robot;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@SuppressLint("SdCardPath")
public class Logger {

    private static final String basePath = "/sdcard/FIRST/robotLogs/RobotData";
    private static FileWriter fileWriter;
    private static BufferedReader bufferedReader;
    private String data = "";

    /**
     * Creates a new file and writes first row
     * This method must be used before logging
     */
    public void startLogging() {
        try {
            data = "";
            File robotDataLog = new File(getLogName(true));
            fileWriter = new FileWriter(robotDataLog);
            fileWriter.write("Timestamp,SinceStart,X,Y,Theta,VelocityX,VelocityY,VelocityTheta,AccelX,AccelY,AccelTheta,NumRings,MagHome,FeedHome,LastTarget\n");
        } catch (Exception e) {e.printStackTrace();}
    }

    /**
     * Searches the list of files to find the last file number that exists
     */
    public static int getLastFileNumber() {
        int logNum = 1;
        while (true) {
            File currentFile = new File(basePath + logNum + ".csv");
            if (!currentFile.exists()) {
                break;
            }
            logNum++;
        }
        return logNum - 1;
    }

    /**
     * Takes the file number from getLastFileNumber() and converts it to a file name
     */
    private static String getLogName(boolean fileWrite) {
        if (fileWrite) {
            Robot.log("Writing to: " + basePath + (getLastFileNumber() + 1) + ".csv");
            return basePath + (getLastFileNumber() + 1) + ".csv";
        } else {
            Robot.log("Reading from: " + basePath + getLastFileNumber() + ".csv");
            return basePath + getLastFileNumber() + ".csv";
        }
    }

    /**
     * Adds data to string
     */
    @SuppressLint("SimpleDateFormat")
    public void logData(double timeSinceSt, double x, double y, double theta, double velocityX, double velocityY, double velocityTheta,
                        double accelX, double accelY, double accelTheta, int numRings, boolean magHome, boolean feedHome, int lastTarget) {
        SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss.SSS");
        data += df.format(new Date())+","+timeSinceSt+","+x+","+y+","+theta+","+velocityX+","+velocityY+","+velocityTheta+","+
                accelX+","+accelY+","+accelTheta+","+numRings+","+magHome+","+feedHome+","+lastTarget+"\n";
    }

    /**
     * Writes string to file, closes file
     */
    public void stopLogging() {
        try {
            fileWriter.write(data);
            fileWriter.close();
        }
        catch (Exception e) {e.printStackTrace();}
    }

    /**
     * Reads position from last written file
     */
    public static double[] readPos() {
        double[] robotPos = new double[] {0, 0, 0};

        try {
            bufferedReader = new BufferedReader(new FileReader(getLogName(false)));
            List<String> lines = bufferedReader.lines().collect(Collectors.toList());
            String[] data = lines.get(lines.size()-2).split(",");
            robotPos = new double[] {Double.parseDouble(data[2]), Double.parseDouble(data[3]), Double.parseDouble(data[4])};

            bufferedReader.close();
            Robot.log("Starting At " + Arrays.toString(robotPos));
        } catch (Exception ex) {
            Robot.log("read error, using default values :-(");
            ex.printStackTrace();
        }

        return robotPos;
    }
}