package org.firstinspires.ftc.teamcode.TeleopPrograms;

import android.util.Log;

import com.acmerobotics.dashboard.config.Config;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import org.firstinspires.ftc.teamcode.Debug.Logger;
import org.firstinspires.ftc.teamcode.RobotClasses.Constants;
import org.firstinspires.ftc.teamcode.RobotClasses.MecanumDrivetrain;
import org.firstinspires.ftc.teamcode.RobotClasses.Robot;

import java.util.Arrays;

@TeleOp(name = "Teleop")
@Config
public class Teleop extends LinearOpMode {

    public int startX = 90;
    public int startY = 9;
    public double startTheta = Math.PI/2;

    private Robot robot;
    public static boolean robotCentric = true;
    public static boolean useAutoPos = true;

    public double xySpeed = 1;
    public double thSpeed = 1;

    // Toggles
    public boolean stickToggle = false;
    public boolean sticksOut = true;
    public boolean isStickAuto = true;
    public boolean downToggle = false;
    public boolean armDown = true;
    public boolean aimLockToggle = false;
    public boolean aimLock = false;

    /*
    Gamepad 1:
    Left trigger- intake on
    Right trigger- intake reverse
    Left bumper- high goal shoot
    Right bumper- powershot shoot
    X- reset odo

    Gamepad 2:
    B- cancel shoot
    Y- pre-rev flywheel for high goal
    X- toggle sticks
    A- blocker up
    Left bumper- aimlock toggle
     */

    @Override
    public void runOpMode() {
        if (useAutoPos) {
            double[] initialPosition = Logger.readPos();
            telemetry.addData("Starting Position", Arrays.toString(initialPosition));
            telemetry.update();
            robot = new Robot(this, initialPosition[0], initialPosition[1], initialPosition[2], false);
        } else {
            robot = new Robot(this, startX, startY, startTheta, false);
        }

        robot.logger.startLogging(false);
        robot.intake.sticksOut();

        waitForStart();

        while (opModeIsActive()) {
            // Intake on/off/rev
            if (gamepad1.left_trigger > 0) {
                robot.intake.on();
            } else if (gamepad1.right_trigger > 0) {
                robot.intake.reverse();
            } else {
                robot.intake.off();
            }

            // High goal and powershot shoot
            if (gamepad1.left_bumper) {
                robot.highGoalShoot();
            } else if (gamepad1.right_bumper) {
                robot.powerShotShoot();
            }

            // Stop shoot sequence
            if (gamepad2.b) {
                robot.cancelShoot();
            }

            // Rev up flywheel for high goal
            if (gamepad2.y || (!robot.shooter.sensorBroken && robot.numRings == 2)) {
                robot.shooter.flywheelHG();
            }

            // Auto raise sticks after 3 rings
            if (!robot.shooter.sensorBroken && robot.numRings == 3) {
                robot.intake.sticksCollect();
            }

            // Stick Retraction/Extension Toggle
            if (gamepad2.x && !stickToggle) {
                stickToggle = true;
                if (sticksOut) {
                    robot.intake.sticksCollect();
                } else {
                    robot.intake.sticksOut();
                }
                sticksOut = !sticksOut;
            } else if (!gamepad2.x && stickToggle) {
                stickToggle = false;
            }

            // Ring blocker
            if (gamepad2.a) {
                robot.intake.blockerUp();
            } else {
                robot.intake.blockerDown();
            }

            // Wobble arm up/down
            if (gamepad2.dpad_down && !downToggle) {
                downToggle = true;
                if (armDown) {
                    robot.wobbleArm.armUp();
                } else {
                    robot.wobbleArm.armDown();
                }
                armDown = !armDown;
            } else if (!gamepad2.dpad_down && downToggle) {
                downToggle = false;
            }

            if (!robot.preShoot && !robot.shoot) {
                if (gamepad2.left_trigger > 0) {
                    robot.intake.stickLeft(Constants.L_OUT_POS);
                } else {
                    robot.intake.stickLeft(Constants.L_HOME_POS);
                }
                if (gamepad2.right_trigger > 0) {
                    robot.intake.stickRight(Constants.R_OUT_POS);
                } else {
                    robot.intake.stickRight(Constants.R_HOME_POS);
                }
            }

            // Slow align mode
            if (gamepad2.right_bumper) {
                xySpeed = 0.22;
                thSpeed = 0.17;
            } else {
                xySpeed = 1;
                thSpeed = 1;
            }

            // Reset odo for powershot
            if (gamepad1.x) {
                robot.resetOdo(111, 63, Math.PI/2);
                robot.thetaOffset = 0;
            }

            // Change shooting theta offset to compensate for odo drift
            if (gamepad2.dpad_left) {
                robot.thetaOffset -= 0.01;
            } else if (gamepad2.dpad_right) {
                robot.thetaOffset += 0.01;
            }

            // Reset theta offset
            if (gamepad2.dpad_up) {
                robot.thetaOffset = 0;
            }

            // Enter aimlock/strafe mode
            if (gamepad2.left_bumper && !aimLockToggle) {
                aimLockToggle = true;
                aimLock = !aimLock;
            } else if (!gamepad2.left_bumper && aimLockToggle) {
                aimLockToggle = false;
            }

            // Drivetrain controls
            if (!aimLock || gamepad2.right_bumper) {
                if (robotCentric) {
                    robot.drivetrain.setControls(-gamepad1.left_stick_y * xySpeed, -gamepad1.left_stick_x * xySpeed, -gamepad1.right_stick_x * thSpeed);
                } else {
                    robot.drivetrain.setGlobalControls(gamepad1.left_stick_y * xySpeed, gamepad1.left_stick_x * xySpeed, -gamepad1.right_stick_x * thSpeed);
                }
            } else if (!robot.preShoot && !robot.shoot) {
                robot.drivetrain.setGlobalControls(gamepad1.left_stick_x * xySpeed, MecanumDrivetrain.yKp * (63 - robot.y) + MecanumDrivetrain.yKd * (-robot.vy), MecanumDrivetrain.thetaKp * (robot.shootTargets(3)[2] - robot.theta) + MecanumDrivetrain.thetaKd * (-robot.w));
            }

            // Update robot
            robot.update();

            // Telemetry
            if (robot.shooter.sensorBroken) {
                telemetry.addData("Distance Sensor", "Broken");
            }
            telemetry.addData("X", robot.x);
            telemetry.addData("Y", robot.y);
            telemetry.addData("Theta", robot.theta);
            telemetry.addData("# Rings", robot.numRings);
            telemetry.addData("Shooter Velocity", robot.shooter.getVelocity());
            telemetry.addData("# Cycles", robot.cycles);
            telemetry.addData("Average Cycle Time", (robot.cycleTotal / robot.cycles) + "s");
            telemetry.update();
        }

        Log.w("cycle-log", "# Cycles: " + robot.cycles);
        Log.w("cycle-log", "Avg cycle Time: " + (robot.cycleTotal / robot.cycles) + "s");
        robot.stop();
    }
}