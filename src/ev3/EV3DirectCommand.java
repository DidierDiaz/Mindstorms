package ev3;

import lejos.nxt.ADSensorPort;
import lejos.nxt.EV3TouchSensor;
import lejos.nxt.Motor;
import lejos.nxt.SensorPort;

/**
 * Utility class methods help move the Grabber EV3 robot around
 */
public class EV3DirectCommand {

	public static void moveForward(int rotations)
	{
    	Motor.B.rotate((180 * rotations)*1, true);
    	Motor.C.rotate((180 * rotations)*1, true);
    	while (Motor.B.isMoving() || Motor.C.isMoving());
    	Motor.B.flt(true);
    	Motor.C.flt(true);
	}

	public static void moveBackwards(int rotations)
	{
    	Motor.B.rotate((180 * rotations)*-1, true);
    	Motor.C.rotate((180 * rotations)*-1, true);		
    	while (Motor.B.isMoving() || Motor.C.isMoving());
    	Motor.B.flt(true);
    	Motor.C.flt(true);
	}
	
	public static void release()
	{		
		// Grabbers already open?
		EV3TouchSensor sensor1 = new EV3TouchSensor((ADSensorPort)SensorPort.S1);
		if(sensor1.isPressed())
			return;
		// Release grabbers		
		Motor.A.resetTachoCount();
		Motor.A.rotate((180 * 4)*-1);
    	Motor.A.flt(true);		
	}

	public static void grab()
	{
		// Grabbers already grabbing something?
		EV3TouchSensor sensor1 = new EV3TouchSensor((ADSensorPort)SensorPort.S1);
		if(sensor1.isPressed()==false)
			return;
		Motor.A.resetTachoCount();
		Motor.A.rotate((180 * 4));		
    	Motor.A.flt(true);		
	}
	
	public static void turnLeft()
	{
    	Motor.B.rotate((int) (180 * 2.4)*-1, true);
    	Motor.C.rotate((int) (180 * 2.4)*1, true);
    	while (Motor.B.isMoving() || Motor.C.isMoving());
    	Motor.B.flt(true);
    	Motor.C.flt(true);
	}
	
	public static void turnRight()
	{
    	Motor.B.rotate((int) (180 * 2.4)*1, true);
    	Motor.C.rotate((int) (180 * 2.4)*-1, true);
    	while (Motor.B.isMoving() || Motor.C.isMoving());
    	Motor.B.flt(true);
    	Motor.C.flt(true);
	}
	
	public static void init()
	{
    	Motor.A.setSpeed(700);
    	Motor.B.setSpeed(700);
    	Motor.C.setSpeed(700);
    	moveForward(1);
	}

    public static void main(String[] args)
        	throws Exception
    {
    	init();
    	release();
    	moveForward(8);
    	turnLeft();
    	moveForward(9);
    	turnRight();
    	moveForward(8);
    	grab();    	
    }
}
