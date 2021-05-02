package org.firstinspires.ftc.teamcode.RobotClasses;

import android.annotation.SuppressLint;
import android.util.Log;

import com.acmerobotics.dashboard.config.Config;
import com.qualcomm.hardware.lynx.LynxModule;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.VoltageSensor;
import com.qualcomm.robotcore.util.ElapsedTime;

import org.firstinspires.ftc.teamcode.Debug.Logger;
import org.firstinspires.ftc.teamcode.OpenCV.Ring;
import org.firstinspires.ftc.teamcode.Pathing.Pose;
import org.firstinspires.ftc.teamcode.Pathing.Target;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.PI;
import static org.firstinspires.ftc.teamcode.Debug.Dashboard.addPacket;
import static org.firstinspires.ftc.teamcode.Debug.Dashboard.drawField;
import static org.firstinspires.ftc.teamcode.Debug.Dashboard.drawLine;
import static org.firstinspires.ftc.teamcode.Debug.Dashboard.drawRing;
import static org.firstinspires.ftc.teamcode.Debug.Dashboard.drawRobot;
import static org.firstinspires.ftc.teamcode.Debug.Dashboard.sendPacket;

@Config
@SuppressWarnings("FieldCanBeLocal")
public class Robot {

    // Robot Classes
    public MecanumDrivetrain drivetrain;
    public Intake intake;
    public WobbleArm wobbleArm;
    public Shooter shooter;
    public T265 t265;
    public Logger logger;

    private ElapsedTime profiler;
    private List<LynxModule> allHubs;
    private VoltageSensor battery;
    private boolean startVoltTooLow = false;

    // Class Constants
    private final int loggerUpdatePeriod = 2;
    private final int sensorUpdatePeriod = 15;
    private final double xyTolerance = 1;
    private final double thetaTolerance = PI/35;

    private double odoCovariance = 1;
    private boolean useT265 = false;

    // State Variables
    private final boolean isAuto;
    private boolean firstLoop = true;
    private int loopCounter = 0;
    public int numRings = 0;
    public boolean highGoal = false;
    public boolean preShoot = false;
    public boolean preShootOverride = false;
    public boolean shootOverride = false;
    public boolean aimLockShoot = false;
    public boolean shoot = false;
    public int lastTarget = -1;

    public int cycles = 0;
    public double cycleTotal;
    public double lastCycleTime;
    public double longestCycle = 0;

    // Time and Delay Variables
    public double curTime;
    public double shootTime;
    public double startShootTime;
    public double flickTime;
    public double shootDelay;
    private int vThresh;
    private int vHighThresh;
    public static double preShootTimeBackup = 4000;
    public static double flickTimeBackup = 1000;
    public static int highGoalDelay = 250;
    public static int psDelay = 500;
    public static double flickDelay = 250;

    // Motion Variables
    public double x, y, theta, vx, vy, w;
    private double prevX, prevY, prevTheta, prevVx, prevVy, prevW, prevTime, ax, ay, a;
    public double startTime;

    // Shooter Variables
    private final double[] shootXCor = {76.5, 84, 91.5, 108};
    private final double shootYCor = 150;
    private double[] target = {};

    public double shootYOverride = 0;
    public double shootYOffset = 0;
    public double flapPos = Constants.FLAP_DOWN_POS;
    public double flapOverride = 0;
    public int numRingsPreset = 3;
    public double thetaOffset = 0;

    // Powershot Debug Variables
    public final double[] psShootPos = new double[] {111, 63};
    public static double theta0 = 1.905;
    public static double theta1 = 1.815;
    public static double theta2 = 1.73;
    public static double[] thetaPositions = {theta2, theta1, theta0};

    // Ring State Variables
    public ArrayList<Ring> shotRings = new ArrayList<>();
    public ArrayList<Ring> ringPos = new ArrayList<>();

    // OpMode Stuff
    private LinearOpMode op;

    // Constructor
    public Robot(LinearOpMode op, double x, double y, double theta, boolean isAuto) {
        this.x = x;
        this.y = y;
        this.theta = theta;
        this.op = op;
        this.isAuto = isAuto;

        drivetrain = new MecanumDrivetrain(op, x, y, theta);
        intake = new Intake(op, isAuto);
        shooter = new Shooter(op);
        wobbleArm = new WobbleArm(op);
        logger = new Logger();
        try {
            t265 = new T265(op, x, y, theta);
            t265.startCam();
        } catch (Exception ex) {
            log("Camera error");
            ex.printStackTrace();
            useT265 = false;
        }

        profiler = new ElapsedTime();

        allHubs = op.hardwareMap.getAll(LynxModule.class);
        for (LynxModule hub : allHubs) {
            hub.setBulkCachingMode(LynxModule.BulkCachingMode.MANUAL);
        }

        battery = op.hardwareMap.voltageSensor.iterator().next();
        log("Battery Voltage: " + battery.getVoltage() + "v");
        if (battery.getVoltage() < 12.4) {
            startVoltTooLow = true;
        }

        drawField();
        drawRobot(this, "black");
        sendPacket();
    }

    // Stop logger and t265
    public void stop() {
        logger.stopLogging();
        if (useT265) {
            t265.stopCam();
        }
    }

    // Reset Odometry
    public void resetOdo(double x, double y, double theta) {
        drivetrain.resetOdo(x, y, theta);
        if (useT265) {
            t265.setCameraPose(x, y, theta);
        }
    }

    public void update() {
        loopCounter++;
        profiler.reset();
        curTime = System.currentTimeMillis();

        // Powershot Debug
        thetaPositions = new double[] {theta2, theta1, theta0};

        // Track time after start
        if (firstLoop) {
            startTime = curTime;
            lastCycleTime = curTime;
            firstLoop = false;
        }

//        if (!isAuto && !preShoot && !shoot && !shooter.sensorBroken && loopCounter % sensorUpdatePeriod == 0) {
//            numRings = shooter.getNumRings();
//        }

        profile(1);

        // Pre-shoot tasks: Turn on flywheel, move robot to shooting position, mag up, start auto-feed once ready
        if (preShoot) {

            // Set flywheel velocity based on what we want to shoot
            if (highGoal) {
                shooter.flywheelHG();
                vThresh = (isAuto ? Constants.HIGH_GOAL_VELOCITY - 100 : 1400);
                vHighThresh = Constants.HIGH_GOAL_VELOCITY + 50;
            } else {
                shooter.flywheelPS();
                vThresh = Constants.POWERSHOT_VELOCITY - 70;
                vHighThresh = Constants.POWERSHOT_VELOCITY + 40;
            }

            // Turn off intake and put mag up
            if (shooter.magHome) {
                intake.off();
                shooter.magShoot();
                if (!isAuto) {
                    intake.sticksShoot();
                }
                log("Mag up");
            }

            // Move to shooting position
            if (!preShootOverride && !aimLockShoot && (!isAtPose(target[0], target[1], target[2] - (highGoal ? thetaOffset : 0), 1, 1, PI/35) || !notMoving())) {
                if (!highGoal) {
                    setTargetPoint(new Target(target[0], target[1], target[2]).thetaKp(3.5).thetaKd(0.10).yKp(0.3).yKd(0.02));
                } else {
                    setTargetPoint(new Target(target[0], target[1], target[2] - thetaOffset).yKp(0.5));
                }
                log("(" + round(x) + ", " + round(y) + ", " + round(theta) + ") (" + round(vx) + ", " + round(vy) + ", " + round(w) + ") Moving to shoot position: [" + round(target[0]) + ", " + round(target[1]) + ", " + round(target[2]) + "]");
                log(shooter.getVelocity() + ", Target: " + shooter.getTargetVelocity() + ", Min: " + vThresh + ", Max: " + vHighThresh);
            }

            // Start auto-feed when mag is up, velocity is high enough, and robot is at position
            if (preShootOverride || (vThresh <= shooter.getVelocity() /*&& shooter.getVelocity() < vHighThresh*/ &&
                    (aimLockShoot || (isAtPose(target[0], target[1], target[2] - (highGoal ? thetaOffset : 0), 1, 1, PI/35) && notMoving())))) {
                if (highGoal) {
                    shootDelay = highGoalDelay;
                    vThresh = Constants.HIGH_GOAL_VELOCITY - 120;
                    vHighThresh = Constants.HIGH_GOAL_VELOCITY + 20;
                } else {
                    shootDelay = psDelay;
                }
                shoot = true;
                numRings = numRingsPreset;
                shootYOverride = 0;
                shootTime = curTime;
                flickTime = curTime;
                preShoot = false;
                preShootOverride = false;
                log("Ready to shoot " + (highGoal ? "high goal" : "powershot") + ", velocity: " + shooter.getVelocity());
                log("Pre shoot time: " +  (curTime - startShootTime) + " ms");
            }

            // If robot does not converge or mag gets stuck
            if (curTime - startShootTime > (isAuto ? 1500 : 3000)) {
                if (!isAuto && highGoal) {
                    cancelShoot();
                } else {
                    log("PS: vel: " + (vThresh <= shooter.getVelocity())  + ", pose: " + isAtPose(target[0], target[1], target[2] - (highGoal ? thetaOffset : 0), 1, 1, PI/35) + ", v: " + notMoving());
                    preShootOverride = true;
                }
                log("Preshoot Timed Out");
            }
        }

        profile(2);

        // Shoot tasks: change/maintain shooting position, auto feed rings
        if (shoot && numRings >= 0 && !shooter.magHome) {
            // Maintain/change robot alignment, set flap
            if (numRings > 0) {
                if (highGoal) {
                    lastTarget = 3;
                    drivetrain.stop();
                } else if (numRings == 3 || curTime - flickTime > flickDelay) {
                    target = new double[] {psShootPos[0], psShootPos[1], thetaPositions[numRings - 1]};
                    lastTarget = 3 - numRings;
                }
                if (!shootOverride && !aimLockShoot) {
                    if (!highGoal) {
                        setTargetPoint(new Target(target[0], target[1], target[2]).thetaKp(3.5).thetaKd(0.10).yKp(0.3).yKd(0.02));
                    } else {
                        setTargetPoint(new Target(target[0], target[1], target[2] - thetaOffset).yKp(0.5));
                    }
                }
            }

            // Auto feed rings
            if (curTime - shootTime > shootDelay) {
                if (numRings > 0) {
                    // Shoot ring only if robot at position and velocity low enough
                    if (((highGoal && (shootOverride || aimLockShoot || ((!isAuto || highGoal && 1600 <= shooter.getVelocity()) && isAtPose(target[0], target[1], target[2] - thetaOffset, 1, 1, PI/60) && notMoving())))
                            || (!highGoal && isAtPose(target[0], target[1], target[2], 0.5, 0.5, PI/300) && notMoving()))
                           /* || curTime - flickTime > flickTimeBackup*/) {

                        log("In shoot Velocity/Target: " + shooter.getVelocity() + "/" + shooter.getTargetVelocity());
                        if (!highGoal) {
                            log("PS pos: " + round(x) + ", " + round(y) + ", " + round(theta) + ", time: " + (curTime - flickTime > flickTimeBackup) + ", xy: " + isAtPose(target[0], target[1], target[2], 0.5, 0.5, PI/35) + ", theta: " + (Math.abs(theta - target[2]) < PI/300));
                        }

                        if (numRings == 3) {
                            if (!isAuto) {
                                intake.sticksOut();
                            }
                            log("Feed ring 1");
                        } else if (numRings == 2) {
                            log("Feed ring 2");
                        } else if (numRings == 1) {
                            log("Feed ring 3");
                        }

                        if (isAuto && !highGoal) {
                            drivetrain.stop();
                        }

                        if (shooter.feedHome) {
                            shooter.feedTop();
                        } else {
                            shooter.feedHome();
                        }
                        shotRings.add(new Ring(x, y, theta + thetaOffset, vx, vy, w, curTime));
                        numRings--;

                        flickTime = curTime;
                    } else {
                        log("W (" + round(x) + ", " + round(y) + ", " + round(theta) + ") (" + round(vx) + ", " + round(vy) + ", " + round(w) + ") Moving to shoot position: [" + round(target[0]) + ", " + round(target[1]) + ", " + round(target[2]) + "]");
                        log(shooter.getVelocity() + ", Target: " + shooter.getTargetVelocity() + ", Min: " + vThresh + ", Max: " + vHighThresh);
                    }
                } else {
                    shooter.flywheelOff();
                    shooter.magHome();
                    shoot = false;
                    shootOverride = false;

                    log("In shoot Velocity/Target: --------------------");
                    log("Shoot done");
                    log("Total shoot time: " +  (curTime - startShootTime) + " ms");
                    double cycleTime = (curTime - lastCycleTime) / 1000;
                    cycleTotal += cycleTime;
                    cycles++;
                    Log.w("cycle-log", "Cycle " + cycles + ": " + cycleTime + "s");
                    if (cycleTime > longestCycle) {
                        longestCycle = cycleTime;
                    }
                    lastCycleTime = curTime;
                }
                shootTime = curTime;
            }
        }

        shooter.setFlapPosition(flapPos + flapOverride);

        profile(3);

        // Update Position
        drivetrain.updatePose();
        if (odoCovariance != 1) {
//             t265.sendOdometryData(vx, vy, theta, w);
            t265.updateCamPose();
        }
        shooter.updateShooter();
        intake.updateSticks();

        profile(4);

        // Calculate Motion Info
        double timeDiff = curTime / 1000 - prevTime;
        if (useT265) {
            x = odoCovariance * drivetrain.x + (1 - odoCovariance) * t265.getX();
            y = odoCovariance * drivetrain.y + (1 - odoCovariance) * t265.getY();
            theta = odoCovariance * drivetrain.theta + (1 - odoCovariance) * t265.getTheta();
        } else {
            x = drivetrain.x;
            y = drivetrain.y;
            theta = drivetrain.theta;
        }
        vx = (x - prevX) / timeDiff;
        vy = (y - prevY) / timeDiff;
        w = (theta - prevTheta) / timeDiff;
        ax = (vx - prevVx) / timeDiff;
        ay = (vy - prevVy) / timeDiff;
        a = (w - prevW) / timeDiff;

        // Remember Previous Motion Info
        prevX = x; prevY = y;
        prevTheta = theta;
        prevTime = curTime / 1000;
        prevVx = vx; prevVy = vy;
        prevW = w;

        profile(5);

        // Log Data
        if (loopCounter % loggerUpdatePeriod == 0) {
            logger.logData(curTime - startTime, x, y, theta, vx, vy, w, ax, ay, a, numRings, shooter.magHome, shooter.feedHome, lastTarget, cycles, cycleTotal / cycles);
        }

        profile(6);

        // Dashboard Telemetry
        if (startVoltTooLow) {
            addPacket("0", "Starting Battery Voltage < 12.4!!!!");
        }
        /*if (shooter.sensorBroken) {
            addPacket("0", "Distance Sensor Broken!!!!");
        }*/
        addPacket("1 X", round(x));
        addPacket("2 Y", round(y));
        addPacket("3 Theta", round(theta));
        addPacket("4 Shooter Velocity", shooter.getVelocity());
        addPacket("4 Target Velocity",  shooter.getTargetVelocity());
        addPacket("5 numRings", numRings);
        addPacket("6 shoot", preShoot  + " " + shoot + " " + highGoal);
        addPacket("7 Run Time", (curTime - startTime) / 1000);
        addPacket("8 Update Frequency (Hz)", round(1 / timeDiff));
        addPacket("9 Pod Zeroes", drivetrain.zero1 + ", " + drivetrain.zero2 + ", " + drivetrain.zero3);
        addPacket("offsets", round(thetaOffset) + " " + round(flapOverride) + " " + shootYOffset);
        addPacket("coeffs", shooter.shooterMotor1.getPIDFCoefficients(DcMotor.RunMode.RUN_USING_ENCODER));
        if (!isAuto) {
            addPacket("Cycle Time", (curTime - lastCycleTime) / 1000);
            addPacket("Average Cycle Time", round(cycleTotal / cycles));
            addPacket("Cycles", cycles);
        }

        // Dashboard Drawings
        drawField();
        drawRobot(this, "black");
//        drawRobot(drivetrain.x, drivetrain.y, drivetrain.theta, "green");
//        if (useT265) {
//            drawRobot(t265.getX(), t265.getY(), t265.getTheta(), t265.confidenceColor());
//        }
        for (Ring ring : ringPos) {
            if (ring.getX() != x && ring.getY() != y) {
                drawRing(ring);
            }
        }
        int shotRingCount = 0;
        while (shotRingCount < shotRings.size()) {
            Ring ring = shotRings.get(shotRingCount);
            ring.updatePose(curTime);
            double[] ringCoords = ring.getAbsCoords();
            if (48 < ringCoords[0] && ringCoords[0] < 144 && 0 < ringCoords[1] && ringCoords[1] < 144) {
                shotRingCount++;
                drawRing(ring);
            } else {
                shotRings.remove(shotRingCount);
            }
        }
        sendPacket();

        profile(7);

        // Clear bulk cache
        for (LynxModule hub : allHubs) {
            hub.clearBulkCache();
        }

        profile(8);
    }

    public void highGoalShoot() {
        highGoalShoot(!shooter.sensorBroken ? shooter.getNumRings() : 3, true);
    }

    public void highGoalShoot(boolean useFlap) {
        highGoalShoot(!shooter.sensorBroken ? shooter.getNumRings() : 3, useFlap);
    }

    public void highGoalShoot(int numRings) {
        highGoalShoot(numRings, true);
    }

    // Set variables for high goal shoot
    public void highGoalShoot(int numRings, boolean useFlap) {
        if (!preShoot && !shoot) {
            preShoot = true;
            highGoal = true;
            numRingsPreset = numRings;
            if (numRings != 3) {
                log("Shooting with " + numRings + " rings");
            }
            startShootTime = curTime;
            if (useFlap) {
                flapPos = Constants.FLAP_UP_POS;
            } else {
                flapPos = Constants.FLAP_DOWN_POS;
            }

            double shootY = 56 + shootYOffset;
            if (isAuto) {
                if (shootYOverride != 0) {
                    shootY = shootYOverride;
                    log("Shooting at y = " + shootY);
                }
            }
            target = shootTargets(x, shootY, PI/2, 3);
            log("High goal shoot initiated");
        }
    }

    // Set variables for powershot shoot
    public void powerShotShoot() {
        if (!preShoot && !shoot) {
            preShoot = true;
            highGoal = false;
            numRingsPreset = 3;
            startShootTime = curTime;
            flapPos = Constants.FLAP_DOWN_POS;
            target = new double[] {psShootPos[0], psShootPos[1], thetaPositions[2]};
            log("Powershot shoot initiated");
        }
    }

    // Cancel shoot sequence
    public void cancelShoot() {
        preShoot = false;
        shoot = false;
        numRings = 0;
        shootYOverride = 0;
        shooter.flywheelOff();
        intake.sticksOut();
        shooter.magHome();
        log("Shoot cancelled");
    }

    // Calculate robot pose for auto aim
    // left ps = 0, middle ps = 1, right ps = 2, high goal = 3
    public double[] shootTargets(int targetNum) {
        return shootTargets(x, y, theta, targetNum);
    }

    public double[] shootTargets(double shootX, double shootY, double shootTheta, int targetNum) {
        /* power1- (76.5,144,24)
           power2- (84,144,24)
           power3- (91.5,144,24)
           high goal- (108,144,35.5) */

        double targetX = shootXCor[targetNum];
        double targetY = shootYCor;

        double shooterX = shootX + (aimLockShoot && 0.5 < vx ? vx : 0) * Shooter.RING_FLIGHT_TIME + Shooter.SHOOTER_DX * Math.sin(shootTheta);
        double shooterY = shootY + (aimLockShoot && 0.5 < vy ? vy : 0) * Shooter.RING_FLIGHT_TIME - Shooter.SHOOTER_DX * Math.cos(shootTheta);
        double dx = targetX - shooterX;
        double dy = targetY - shooterY;

        // Uses Angle Bisector for High Goal for more consistency
        if (targetNum == 3) {
            double d = 8;
            double a = Math.sqrt(Math.pow(dx + d/2, 2) + Math.pow(dy, 2));
            double b = Math.sqrt(Math.pow(dx - d/2, 2) + Math.pow(dy, 2));

            targetX += - d/2 + d * b / (a + b);
            dx = targetX - shooterX;
            drawLine(shooterX, shooterY, targetX, targetY, "blue");
        }

        // Calculate Robot Angle
        double d = Math.sqrt(Math.pow(targetX - shooterX, 2) + Math.pow(targetY - shooterY, 2));
        double alignRobotAngle = Math.atan2(dy, dx) + 0.0013 * d - 0.2300;
        double alignRobotX = shooterX - Shooter.SHOOTER_DX * Math.sin(alignRobotAngle);
        double alignRobotY = shooterY + Shooter.SHOOTER_DX * Math.cos(alignRobotAngle);

        return new double[] {alignRobotX, alignRobotY, alignRobotAngle};
    }

    // Set target point (velocity specification, custom Kp and Kv values)
    public void setTargetPoint(double xTarget, double yTarget, double thetaTarget, double vxTarget, double vyTarget, double wTarget, double xKp, double yKp, double thetaKp, double xKd, double yKd, double thetaKd) {
        // Make Sure thetaTarget is Between 0 and 2pi
        thetaTarget = thetaTarget % (2*PI);
        if (thetaTarget < 0) {
            thetaTarget += 2*PI;
        }

        // Picking the Smaller Distance to Rotate
        double thetaControl;
        if (Math.abs(theta - thetaTarget) > PI) {
            thetaControl = Math.abs(theta - thetaTarget) / (theta - thetaTarget) * (Math.abs(theta - thetaTarget) - 2*PI);
        } else {
            thetaControl = theta - thetaTarget;
        }

        drawRobot(xTarget, yTarget, thetaTarget, "blue");
//        Robot.log(xTarget + " " + yTarget + " " + thetaTarget);

        drivetrain.setGlobalControls(xKp * (xTarget - x) + xKd * (vxTarget - vx), yKp * (yTarget - y) + yKd * (vyTarget - vy), thetaKp * (-thetaControl) + thetaKd * (wTarget - w));
    }

    // Set target point (default Kp and Kv gains)
    public void setTargetPoint(double xTarget, double yTarget, double thetaTarget) {
        setTargetPoint(xTarget, yTarget, thetaTarget, 0, 0, 0, MecanumDrivetrain.xKp, MecanumDrivetrain.yKp, MecanumDrivetrain.thetaKp, MecanumDrivetrain.xKd, MecanumDrivetrain.yKd, MecanumDrivetrain.thetaKd);
    }

    // Set target point (using pose, velocity specification, default Kp and Kv gains)
    public void setTargetPoint(Pose pose) {
        setTargetPoint(pose.x, pose.y, pose.theta, pose.vx, pose.vy, pose.w, MecanumDrivetrain.xKp, MecanumDrivetrain.yKp, MecanumDrivetrain.thetaKp, MecanumDrivetrain.xKd, MecanumDrivetrain.yKd, MecanumDrivetrain.thetaKd);
    }

    // Set target point (using pose, custom theta and omega, default Kp and Kv gains)
    public void setTargetPoint(Pose pose, double theta, double w) {
        setTargetPoint(pose.x, pose.y, theta, pose.vx, pose.vy, w, MecanumDrivetrain.xKp, MecanumDrivetrain.yKp, MecanumDrivetrain.thetaKp, MecanumDrivetrain.xKd, MecanumDrivetrain.yKd, MecanumDrivetrain.thetaKd);
    }

    // Set target point (using target object)
    public void setTargetPoint(Target target) {
        Pose pose = target.getPose();
        setTargetPoint(pose.x, pose.y, pose.theta, pose.vx, pose.vy, pose.w, target.xKp(), target.yKp(), target.thetaKp(), target.xKd(), target.yKd(), target.thetaKd());
    }

    // Check if robot is at a certain point/angle (default tolerance)
    public boolean isAtPose(double targetX, double targetY, double targetTheta) {
        return isAtPose(targetX, targetY, targetTheta, xyTolerance, xyTolerance, thetaTolerance);
    }

    // Check if robot is at a certain point/angle (custom tolerance)
    public boolean isAtPose(double targetX, double targetY, double targetTheta, double xTolerance, double yTolerance, double thetaTolerance) {
        return Math.abs(x - targetX) < xTolerance && Math.abs(y - targetY) < yTolerance && Math.abs(theta - targetTheta) < thetaTolerance;
    }

    public boolean notMoving() {
        if (isAuto || !highGoal) {
            return notMoving(3.0, 0.3);
        }
        return true;
    }

    public boolean notMoving(double xyThreshold, double thetaThreshold) {
        return (Math.hypot(vx, vy) < xyThreshold && Math.abs(w) < thetaThreshold);
    }

    // Logging
    public static void log(String message) {
        Log.w("robot-log", message);
    }

    private void profile(int num) {
//        Log.w("profiler", num + ": " + profiler.milliseconds());
    }

    @SuppressLint("DefaultLocale")
    public static double round(double num) {
        return Double.parseDouble(String.format("%.5f", num));
    }
}
