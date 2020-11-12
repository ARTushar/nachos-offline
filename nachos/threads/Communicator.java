package nachos.threads;

import nachos.machine.*;

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
    boolean read = false;
    Lock conditionLock = null;
    Condition conditionVarSpeaker = null;
    Condition conditionVarListener = null;
    public Communicator() {
        conditionLock = new Lock();
        conditionVarSpeaker = new Condition(conditionLock);
        conditionVarListener = new Condition(conditionLock);
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
        speakerPresent = true;
        while (!listenerPresent) conditionVarSpeaker.sleep();
        conditionVarListener.wake();
        this.word = word;
        spoken = true;
        while (!read){
        }
        read = false;
        speakerPresent = false;
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
        listenerPresent = true;
        while (!speakerPresent) conditionVarListener.sleep();
        conditionVarSpeaker.wake();
        while (!spoken){
        }
        int data = word;
        read = true;
        spoken = false;
        listenerPresent = false;
        conditionLock.release();
        return data;
    }
}
