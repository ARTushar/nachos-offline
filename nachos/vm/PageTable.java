package nachos.vm;

import nachos.machine.Machine;
import nachos.machine.TranslationEntry;

import java.util.Hashtable;
import java.util.Random;

public class PageTable {
    private Hashtable<PageTableKey, TranslationEntry> pageTable;
    private TransEntryWithPID[] physicalPageToEntryMap;
    public static PageTable pageTableInstance = null;

    private PageTable() {
        pageTable = new Hashtable<>();
        physicalPageToEntryMap = new TransEntryWithPID[Machine.processor().getNumPhysPages()];
    }

    public static PageTable getPageTable() {
        if (pageTableInstance == null) {
            pageTableInstance = new PageTable();
        }
        return pageTableInstance;
    }

    public TranslationEntry getEntry (PageTableKey key) {
        return pageTable.get(key);
    }

    public boolean insertEntry (int processId, TranslationEntry entry) {
        PageTableKey key = new PageTableKey(processId, entry.vpn);
        if (pageTable.containsKey(key)) {
            System.out.println("This entry is already in the page table");
            return false;
        }
        pageTable.put(key, entry);
        if (entry.valid) {
            physicalPageToEntryMap[entry.ppn] = new TransEntryWithPID(processId, entry);
        }
        return true;
    }

    public boolean deleteEntry (int processId, int vpn) {
        PageTableKey key = new PageTableKey(processId, vpn);
        if (!pageTable.containsKey(key)) {
            System.out.println("No entry in the page table to delete");
            return false;
        }
        physicalPageToEntryMap[pageTable.get(key).ppn] = null;
        pageTable.remove(key);
        return true;
    }

    public boolean updateEntry (int processId, TranslationEntry entry) {
        PageTableKey key = new PageTableKey(processId, entry.vpn);
        if (!pageTable.containsKey(key)) {
            System.out.println("No entry in the page table to update");
            return false;
        }
        if (entry.valid) {
            TranslationEntry oldEntry = pageTable.get(key);
            if (entry.dirty) {
                oldEntry.dirty = true;
            }
            if (entry.used) {
                oldEntry.used = true;
            }
            oldEntry.ppn = entry.ppn;
            physicalPageToEntryMap[oldEntry.ppn] = new TransEntryWithPID(processId, oldEntry);
        }
        return true;
    }

    public TransEntryWithPID getReplacementEntry () {
        TransEntryWithPID entry = null;
        Random random = new Random();
        while (entry == null) {
            entry = physicalPageToEntryMap[random.nextInt(physicalPageToEntryMap.length)];
        }
        return entry;
    }

}
