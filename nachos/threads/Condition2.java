package nachos.threads;

import nachos.machine.*;

import java.util.LinkedList;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 *
 * <p>
 * You must implement this.
 *
 * @see	nachos.threads.Condition
 */
public class Condition2 {
  /**
   * Allocate a new condition variable.
   *
   * @param	conditionLock	the lock associated with this condition
   *				variable. The current thread must hold this
   *				lock whenever it uses <tt>sleep()</tt>,
   *				<tt>wake()</tt>, or <tt>wakeAll()</tt>.
   */
  public Condition2(Lock conditionLock) {
    this.conditionLock = conditionLock;
  }

  /**
   * Atomically release the associated lock and go to sleep on this condition
   * variable until another thread wakes it using <tt>wake()</tt>. The
   * current thread must hold the associated lock. The thread will
   * automatically reacquire the lock before <tt>sleep()</tt> returns.
   */
  private ThreadQueue waitQueue =
      ThreadedKernel.scheduler.newThreadQueue(false);
  private int size = 0;

  public void sleep() {
    Lib.assertTrue(conditionLock.isHeldByCurrentThread());

    conditionLock.release();

    boolean intStatus = Machine.interrupt().disable();
    waitQueue.waitForAccess(KThread.currentThread());
    size++;
    KThread.sleep();
    Machine.interrupt().restore(intStatus);

    conditionLock.acquire();
  }

  /**
   * Wake up at most one thread sleeping on this condition variable. The
   * current thread must hold the associated lock.
   */
  public void wake() {
    Lib.assertTrue(conditionLock.isHeldByCurrentThread());

    boolean intStatus = Machine.interrupt().disable();

    KThread thread = waitQueue.nextThread();
    if (thread != null) {
      thread.ready();
      size--;
    }

    Machine.interrupt().restore(intStatus);
  }

  /**
   * Wake up all threads sleeping on this condition variable. The current
   * thread must hold the associated lock.
   */
  public void wakeAll() {
    Lib.assertTrue(conditionLock.isHeldByCurrentThread());

    while (size != 0){
      wake();
    }
  }

  private Lock conditionLock;

  private static class PingTest implements Runnable{

    PingTest(){
      run();
    }

    @Override
    public void run() {
      LinkedList<Integer> items = new LinkedList<>();
      Integer capacity =  5;

      PC pc = new PC(items, capacity);

      Producer producer = new Producer(pc);
      Consumer consumer = new Consumer(pc);

      new KThread(producer).fork();
      new KThread(consumer).fork();
    }

    private static class PC {
      private Lock mutex;
      private Condition2 freeSlotCondition;
      private Condition2 fullSlotCondition;
      private LinkedList<Integer> items;
      private Integer capacity;

      PC(LinkedList<Integer> items, Integer capacity){
        mutex = new Lock();
        freeSlotCondition = new Condition2(mutex);
        fullSlotCondition = new Condition2(mutex);

        this.items = items;
        this.capacity = capacity;
      }

      public void produce(){
        mutex.acquire();

        while (items.size() == capacity){
          freeSlotCondition.sleep();
        }

        int item = items.size() + 1;
        items.add(item);
        System.out.println("Producer produced: " + item);

        fullSlotCondition.wake();

        mutex.release();
      }

      public void consume(){
        mutex.acquire();

        while (items.size() == 0){
          fullSlotCondition.sleep();
        }

        int item = items.removeFirst();
        freeSlotCondition.wake();
        mutex.release();
        System.out.println("Consumer consumed : " + item);
      }
    }

    }

    private static class Producer implements Runnable{

      PingTest.PC pc;

      Producer(PingTest.PC pc){
        this.pc = pc;
      }

      @Override
      public void run() {
        for(int i = 0; i < 100; i++){
          pc.produce();
        }
      }
    }

    private static class Consumer implements Runnable{
      PingTest.PC pc;

      Consumer(PingTest.PC pc){
        this.pc = pc;
      }

      @Override
      public void run() {
        for(int i = 0; i < 100; i++){
          pc.consume();
        }
      }

      private Lock mutex;
      private Lock freeSlot;
      private Lock fullSlot;
      private Condition freeSlotCondition;
      private Condition fullSlotCondition;
      private LinkedList<Integer> items;
      private Integer capacity;

      Consumer(LinkedList<Integer> items, Integer capacity){
        mutex = new Lock();
        freeSlot = new Lock();
        fullSlot = new Lock();
        freeSlotCondition = new Condition(freeSlot);
        fullSlotCondition = new Condition(fullSlot);

        this.items = items;
        this.capacity = capacity;
      }

  }

  public static void selfTest(){
      new PingTest();
  }
}
