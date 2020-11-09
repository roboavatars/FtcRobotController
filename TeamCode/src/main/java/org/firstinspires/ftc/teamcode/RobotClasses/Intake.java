package org.firstinspires.ftc.teamcode.RobotClasses;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DistanceSensor;

import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;

public class Intake {

    private DcMotorEx intakeMotor;
    private DistanceSensor ringSensor;

    public Intake(LinearOpMode op) {
        intakeMotor = op.hardwareMap.get(DcMotorEx.class, "intakeMotor");
        ringSensor = op.hardwareMap.get(DistanceSensor.class, "ringSensor");

        setPower(0);

        op.telemetry.addData("Status", "Intake initialized");
    }

    public void setPower(double power) {
        intakeMotor.setPower(power);
    }

    public void intakeOff() {
        intakeMotor.setPower(0);
    }

    public double getDistance() {
        return ringSensor.getDistance(DistanceUnit.INCH);
    }

}
