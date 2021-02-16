package org.firstinspires.ftc.teamcode.OpenCV.RingLocator;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.util.ElapsedTime;

import org.firstinspires.ftc.teamcode.OpenCV.Ring;
import org.firstinspires.ftc.teamcode.Pathing.Path;
import org.firstinspires.ftc.teamcode.Pathing.Waypoint;
import org.firstinspires.ftc.teamcode.RobotClasses.Robot;

import java.util.ArrayList;

import static java.lang.Math.PI;
import static org.firstinspires.ftc.teamcode.Debug.Dashboard.*;

@TeleOp(name = "Advanced Ring Locator Pipeline Test")
public class AdvancedRingLocatorTest extends LinearOpMode {

    private Robot robot;
    private RingLocator detector;

    private ArrayList<Ring> rings;
    private double ringTime = 0;
    private Path ringPath;

    private double intakePower = 0;
    private boolean start = false;

    @Override
    public void runOpMode() {
        robot = new Robot(this, 87, 63, PI/2, false);
        robot.intake.blockerDown();
        detector = new RingLocator(this);
        detector.start();

        rings = detector.getRings(robot.x, robot.y, robot.theta);
        for (Ring ring : rings) {
            drawRing(ring);
        }
        sendPacket();

        waitForStart();

        // camera warmup / buffer frames
        for (int i = 0; i < 100; i++) {
            rings = detector.getRings(robot.x, robot.y, robot.theta);
            for (Ring ring : rings) {
                drawRing(ring);
            }
            sendPacket();
        }
        ElapsedTime timer = new ElapsedTime();

        ArrayList<Waypoint> ringWaypoints = new ArrayList<>();
        ringWaypoints.add(new Waypoint(robot.x, robot.y, robot.theta, 50, 60, 0, 0));

        double[] ringPos = rings.get(0).driveToRing(robot.x, robot.y);
        if (rings.size() >= 1) {
            if (ringPos[1] > 135) ringPos[2] = PI/2;
            ringWaypoints.add(new Waypoint(ringPos[0], ringPos[1], ringPos[2], 20, 30, 0, ringTime += 1.0));
        }
        if (rings.size() >= 2) {
            ringPos = rings.get(1).driveToRing(ringPos[0], ringPos[1]);
            if (ringPos[1] > 135) ringPos[2] = PI/2;
            ringWaypoints.add(new Waypoint(ringPos[0], ringPos[1], ringPos[2], 20, 10, 0, ringTime += 1.0));
        }
        if (rings.size() == 3) {
            ringPos = rings.get(2).driveToRing(ringPos[0], ringPos[1]);
            if (ringPos[1] > 135) ringPos[2] = PI/2;
            ringWaypoints.add(new Waypoint(ringPos[0], ringPos[1], ringPos[2], 20, 10, 0, ringTime += 1.0));
        }
        ringPath = new Path(ringWaypoints);

        while (opModeIsActive()) {
            for (Ring ring : rings) {
                drawRing(ring);
            }

            if (gamepad1.right_trigger > 0) {
                intakePower = 1;
            } else if (gamepad1.left_trigger > 0) {
                intakePower = -1;
            } else if (!start) {
                intakePower = 0;
            }

            if (gamepad1.left_bumper) {
                robot.highGoalShoot();
            } else if (gamepad1.right_bumper) {
                robot.powerShotShoot();
            }

            if (gamepad1.y) {
                robot.shooter.flywheelHG();
            }

            if (gamepad1.left_trigger != 0) {
                robot.resetOdo(87, 63, PI/2);
            }

            if (robot.numRings == 0) {
                robot.intake.autoSticks(robot.x, robot.y, robot.theta, 6);
            }

            if (gamepad1.b) {
                start = true;
                timer.reset();
            }

            if (start) {
                double curTime = timer.seconds();
                robot.setTargetPoint(ringPath.getRobotPose(curTime));
                intakePower = 1;

                if (curTime > ringTime) {
                    start = false;
                    intakePower = 0;
                }
            }

            robot.intake.setPower(intakePower);
            robot.update();
        }

        detector.stop();
    }
}