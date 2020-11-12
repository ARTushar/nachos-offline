package nachos.threads;

import nachos.machine.*;

import javax.sound.midi.Soundbank;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {
  /**
   * Allocate a new communicator.
   */
  int word;
  boolean speakerPresent = false;
  boolean listenerPresent = false;
  boolean spoken = false;
  boolean listened = false;
  int readerCount = 0;
  int speakerCount = 0;
  Lock conditionLock = null;
  Condition conditionVarSpeaker = null;
  Condition conditionVarListener = null;
  Condition moreReader;
  Condition moreWriter;
  Condition written;
  Condition read;

  public Communicator() {
    conditionLock = new Lock();
    conditionVarSpeaker = new Condition(conditionLock);
    conditionVarListener = new Condition(conditionLock);
    moreReader = new Condition(conditionLock);
    moreWriter = new Condition(conditionLock);
    written = new Condition(conditionLock);
    read = new Condition(conditionLock);
  }

  /**
   * Wait for a thread to listen through this communicator, and then transfer
   * <i>word</i> to the listener.
   *
   * <p>
   * Does not return until this thread is paired up with a listening thread.
   * Exactly one listener should receive <i>word</i>.
   *
   * @param	word	the integer to transfer.
   */
  public void speak(int word) {
    conditionLock.acquire();
    speakerCount++;
    while(speakerCount > 1) moreWriter.sleep();
    speakerPresent = true;
    while (!listenerPresent) conditionVarSpeaker.sleep();
    this.word = word;
    spoken = true;
    written.wake();
    conditionLock.release();
    conditionLock.acquire();
    while(!listened)  {
      read.sleep();
    }
    listened = false;
    speakerPresent = false;
    speakerCount--;
    if(speakerCount != 0) moreWriter.wake();
    conditionLock.release();
  }

  /**
   * Wait for a thread to speak through this communicator, and then return
   * the <i>word</i> that thread passed to <tt>speak()</tt>.
   *
   * @return	the integer transferred.
   */
  public int listen() {
    conditionLock.acquire();
    readerCount++;
    while(readerCount > 1) moreReader.sleep();
    listenerPresent = true;
    while (!speakerPresent) conditionVarListener.sleep();
    conditionVarSpeaker.wake();
    conditionLock.release();
    conditionLock.acquire();
    while(!spoken){
      written.sleep();
    }
    spoken = false;
    int data = word;
    listened = true;
    read.wake();
    conditionLock.release();
    conditionLock.acquire();
    listenerPresent = false;
    readerCount--;
    if(readerCount != 0) moreReader.wake();
    conditionLock.release();
    return data;
  }

  public static void selfTest(){
    new PingTest().run();
  }

  private static class PingTest implements Runnable {

    @Override
    public void run() {
      Communicator communicator = new Communicator();
      Sender sender = new Sender(communicator);
      Receiver receiver = new Receiver(communicator);

      new KThread(sender).fork();
      new KThread(receiver).fork();
    }

    private static class Sender implements Runnable{

      Communicator communicator;

      Sender(Communicator communicator){
        this.communicator = communicator;
      }

      @Override
      public void run() {
        for(int i = 0; i < 10; i++){
          System.out.println("Sent: " + i);
          communicator.speak(i);
        }
        System.out.println("Speaker finished");
      }
    }
    private static class Receiver implements Runnable{

      Communicator communicator;

      Receiver(Communicator communicator){
        this.communicator = communicator;
      }

      @Override
      public void run() {
        for(int i = 0; i < 10; i++){
          int data = communicator.listen();
          System.out.println("Listened: " + data);
        }
        System.out.println("Listener finished");
      }
    }
  }
}
