package nachos.vm;

import nachos.machine.Machine;
import nachos.machine.OpenFile;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;

import static nachos.machine.Processor.pageSize;

public class Swapper {

  private OpenFile swapFile;
  private String swapFileName;
  private HashSet<PageTableKey> unallocatedPages;
  private Hashtable<PageTableKey, Integer> swapTable;
  private LinkedList<Integer> availableLocations;

  private static Swapper singleToneInstance;


  private Swapper() {
    this.swapFileName = "swapFile";
    this.swapFile = Machine.stubFileSystem().open(swapFileName, true);

    if(swapFile == null){
      System.out.println("cannot open the swap file!");
    }

    swapTable = new Hashtable<>();
    unallocatedPages = new HashSet<>();
    availableLocations = new LinkedList<>();
  }

  public static Swapper getInstance() {
    if(singleToneInstance == null){
      singleToneInstance = new Swapper();
    }
    return singleToneInstance;
  }

  public void insertUnallocatedPage(int processId, int vpn) {
    unallocatedPages.add(new PageTableKey(processId, vpn));
  }

  public int allocateIndex(int processId, int vpn) {
    PageTableKey key = new PageTableKey(processId, vpn);
    if(unallocatedPages.contains(key)) {
      unallocatedPages.remove(key);
      if(availableLocations.isEmpty()) {
        availableLocations.add(swapTable.size());
      }

      int index = availableLocations.removeFirst();
      swapTable.put(key, index);
      return index;
    } else {
      Integer index = swapTable.get(key);
      if(index == null) {
        System.out.println("Not found in both the unallocated and allocated");
        return -1;
      }
      return index;
    }

  }

  public void deleteIndex(int processId, int vpn) {
    PageTableKey key = new PageTableKey(processId, vpn);
    if(!swapTable.contains(key)) return;
    int removedIndex = swapTable.remove(key);
    availableLocations.add(removedIndex);
  }

  public byte[] readFromSwapFile(int processId, int vpn) {
    int index = findInSwapTable(processId, vpn);
    if(index == -1) {
      System.out.println("No contents found in the swap file");
      return new byte[pageSize];
    }

    byte[] contents = new byte[pageSize];
    int length = swapFile.read(index * pageSize, contents, 0, pageSize);
    if(length == -1) {
      System.out.println("Failed to read from swap ");
      return new byte[pageSize];
    }
    return contents;
  }

  public int writeInSwapFile(int processId, int vpn, byte[] contents, int offset) {
    int index = allocateIndex(processId, vpn);
    if(index == -1) {
      System.out.println("Failed to allocate index!");
    } else {
      swapFile.write(index * pageSize, contents, offset, pageSize);
    }
    return index;
  }

  public void closeSwapfile() {
    if(swapFile != null) {
      swapFile.close();
      Machine.stubFileSystem().remove("swapFile");
      swapFile = null;
    }
  }

  private int findInSwapTable(int processId, int vpn) {
    PageTableKey key = new PageTableKey(processId, vpn);
    Integer index = swapTable.get(key);
    return index == null ? -1: index;
  }
}

