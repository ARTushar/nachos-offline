package nachos.vm;

import nachos.machine.TranslationEntry;

public class TransEntryWithPID {
    private int processId;
    private TranslationEntry entry;

    public TransEntryWithPID(int processId, TranslationEntry entry) {
        this.processId = processId;
        this.entry = entry;
    }

    public int getProcessId() {
        return processId;
    }

    public TranslationEntry getEntry() {
        return entry;
    }
}
