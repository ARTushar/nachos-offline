package nachos.threads;

import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     *
     * <p><b>Note</b>: Nachos will not function correctly with more than one
     * alarm.
     */

    Semaphore binaryLock = new Semaphore(1);
    KThread callerThread = null;
    long wakeTime = 0;

    public Alarm() {
	Machine.timer().setInterruptHandler(new Runnable() {
		public void run() { timerInterrupt(); }
	    });
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread
     * that should be run.
     */
    public void timerInterrupt() {
	    if (callerThread != null && wakeTime < Machine.timer().getTime()) {
	        callerThread.ready();
	        callerThread = null;
	        wakeTime = 0;
	        binaryLock.V();
        }
	    KThread.yield();
    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks,
     * waking it up in the timer interrupt handler. The thread must be
     * woken up (placed in the scheduler ready set) during the first timer
     * interrupt where
     *
     * <p><blockquote>
     * (current time) >= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param	x	the minimum number of clock ticks to wait.
     *
     * @see	nachos.machine.Timer#getTime()
     */
    public void waitUntil(long x) {
      binaryLock.P();
      wakeTime = Machine.timer().getTime() + x;
      callerThread = KThread.currentThread();
      boolean status = Machine.interrupt().disable();
      KThread.sleep();
      Machine.interrupt().restore(status);
    }

  public static void selfTest() {
    new PingTest().run();
  }

  private static class PingTest implements Runnable{


    @Override
    public void run() {
      Alarm alarm = new Alarm();
      System.out.println("Calling alarm at " + Machine.timer().getTime() +
          " for 1000");
      alarm.waitUntil(1000);

      System.out.println("Waken at " + Machine.timer().getTime());
    }
  }
}
