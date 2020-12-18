package nachos.vm;

import nachos.machine.*;
import nachos.userprog.UserKernel;
import nachos.userprog.UserProcess;

import javax.sound.midi.Soundbank;
import java.util.Hashtable;
import java.util.Random;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
  /**
   * Allocate a new process.
   */
  public VMProcess() {
    super();
  }

  /**
   * Save the state of this process in preparation for a context switch.
   * Called by <tt>UThread.saveState()</tt>.
   */
  public void saveState() {
    super.saveState();
  }

  /**
   * Restore the state of this process after a context switch. Called by
   * <tt>UThread.restoreState()</tt>.
   */
  public void restoreState() {
    super.restoreState();
  }

  public int vaddrToVpn (int vaddr) {
    return Machine.processor().pageFromAddress(vaddr);
  }

  public TranslationEntry findInPageTable (int vpn) {
    return PageTable.getPageTable().getEntry(new PageTableKey(getProcessId(), vpn));
  }

  @Override
  public int translateVirtualToPhysicalAddress(int virtualAddress) {
    int offset = virtualAddress & ((1<<10)-1);
//		System.out.println((1<<10)-1);
    int vpn = (virtualAddress>>>10);
//		System.out.println("vpn: " + vpn);
    String key = Integer.toString(getProcessId()) + vpn;
    int ppn = findInPageTable(vpn).ppn;
//		System.out.println("ppn: "+ppn);
//		System.out.println("paddr: "+(ppn<<10)+offset);
    return (ppn<<10)+offset;
  }

  /**
   * Initializes page tables for this process so that the executable can be
   * demand-paged.
   *
   * @return	<tt>true</tt> if successful.
   */


  public void swapToMemory (int vpn, int ppn) {
    TranslationEntry entry = findInPageTable(vpn);
    if (entry == null || !entry.valid) {
      System.out.println("Invalid swap");
      return;
    }
    byte[] memory = Machine.processor().getMemory();
    byte[] pageContents = Swapper.getInstance().readFromSwapFile(getProcessId(), vpn);
    System.arraycopy(memory, entry.ppn*pageSize, pageContents, 0, pageSize);
  }

  public void memoryToSwap (int vpn) {

  }

  @Override
  protected boolean loadSections() {
//    return super.loadSections();
    if (numPages > UserKernel.availablePageList.size()){
      coff.close();
      Lib.debug(dbgProcess, "\tinsufficient physical memory");
      return false;
    }
    System.out.println("loading sections for " + getProcessId());
    int pagesAdded = 0;
    // load sections
    for (int s=0; s<coff.getNumSections(); s++) {
      CoffSection section = coff.getSection(s);

      Lib.debug(dbgProcess, "\tinitializing " + section.getName()
          + " section (" + section.getLength() + " pages)");

      int vpn = section.getFirstVPN();
      System.out.println("Total Pages: " + numPages);
      for (int i=0; i< section.getLength(); i++) {
        // mapping virtual to physical address
        UserKernel.lock.acquire();
        int phyPageNum = UserKernel.useNextAvailablePage();
        System.out.println("vpn: " + vpn + " ppn: " + phyPageNum);
        TranslationEntry entry = new TranslationEntry(vpn, phyPageNum, true, false, false, false);
//        System.out.println("Entry: " + entry);
        if(section.isReadOnly()) entry.readOnly = true;
        PageTable.getPageTable().insertEntry(getProcessId(), entry);
        Swapper.getInstance().insertUnallocatedPage(getProcessId(), vpn);
        UserKernel.lock.release();
        section.loadPage(i, phyPageNum);
        vpn++;
        pagesAdded++;
      }



//      while(true){
//
//      }
    }
    int vpn = pagesAdded;
    for(int i = pagesAdded; i < numPages; i++) {
      UserKernel.lock.acquire();
      int phyPageNum = UserKernel.useNextAvailablePage();
//      System.out.println("vpn: " + vpn + " key : " + key );
      TranslationEntry entry = new TranslationEntry(vpn, phyPageNum, true, false, false, false);
      System.out.println("vpn : " + vpn + " ppn: " + phyPageNum);
//      System.out.println("Entry: " + entry);
      PageTable.getPageTable().insertEntry(getProcessId(), entry);
      Swapper.getInstance().insertUnallocatedPage(getProcessId(), vpn);
      UserKernel.lock.release();
      vpn++;
    }

    return true;
  }

  /**
   * Release any resources allocated by <tt>loadSections()</tt>.
   */
  protected void unloadSections() {
//    super.unloadSections();
    System.out.println("unloading sections for " + getProcessId());
    int pagesAdded = 0;
    for (int s=0; s<coff.getNumSections(); s++) {
      CoffSection section = coff.getSection(s);

      int vpn = section.getFirstVPN();
      for (int i=0; i< section.getLength(); i++) {
//				 mapping virtual to physical address
        String key = Integer.toString(getProcessId()) + vpn;
        UserKernel.lock.acquire();
        int phyPageNum = PageTable.getPageTable().getEntry(new PageTableKey(getProcessId(), vpn)).ppn;
        System.out.println("vpn : " + vpn + " ppn: " + phyPageNum);
        PageTable.getPageTable().deleteEntry(getProcessId(), vpn);
        UserKernel.addNewAvailablePage(phyPageNum);
        UserKernel.lock.release();
        vpn++;
        pagesAdded++;
      }
    }
    int vpn = pagesAdded;
    for(int i = pagesAdded; i < numPages; i++){
      String key = Integer.toString(getProcessId()) + vpn;
      UserKernel.lock.acquire();
      int phyPageNum = PageTable.getPageTable().getEntry(new PageTableKey(getProcessId(), vpn)).ppn;
      PageTable.getPageTable().deleteEntry(getProcessId(), vpn);
      System.out.println("vpn : " + vpn + " ppn: " + phyPageNum);
      UserKernel.addNewAvailablePage(phyPageNum);
      UserKernel.lock.release();
      vpn++;
    }
    return;
  }

  private void handlePageMiss(int vpn) {

  }

  private void handleTLBWrite(TranslationEntry entry) {
    Random random = new Random();
    int index = random.nextInt(Machine.processor().getTLBSize());
    TranslationEntry oldtlbentry = Machine.processor().readTLBEntry(index);
    if (oldtlbentry.valid) {
      PageTable.getPageTable().updateEntry(getProcessId(), oldtlbentry);
    }
//    System.out.println("tlb writing in index " + index);
    Machine.processor().writeTLBEntry(index, entry);
  }

  private void handleTLBMiss(int virtualAddress) {
    int vpn = vaddrToVpn(virtualAddress);
    TranslationEntry entry = findInPageTable(vpn);

    if(entry == null || !entry.valid) {
      handlePageMiss(vpn);
    } else {
      handleTLBWrite(entry);
    }

  }

  /**
   * Handle a user exception. Called by
   * <tt>UserKernel.exceptionHandler()</tt>. The
   * <i>cause</i> argument identifies which exception occurred; see the
   * <tt>Processor.exceptionZZZ</tt> constants.
   *
   * @param	cause	the user exception that occurred.
   */
  public void handleException(int cause) {
    Processor processor = Machine.processor();
    // deallocate allocated pages

    switch (cause) {
      case Processor.exceptionSyscall:
        int result = handleSyscall(processor.readRegister(Processor.regV0),
            processor.readRegister(Processor.regA0),
            processor.readRegister(Processor.regA1),
            processor.readRegister(Processor.regA2),
            processor.readRegister(Processor.regA3)
        );
        processor.writeRegister(Processor.regV0, result);
        processor.advancePC();
        break;

      case Processor.exceptionTLBMiss:
        handleTLBMiss(Machine.processor().readRegister((Processor.regBadVAddr)));
        break;

      default:
        if(parentProcess != null){
          parentProcess.childProcesesStatus.replace(getProcessId(), -1);
        }
        System.out.println(cause);
        Lib.debug(dbgProcess, "Unexpected exception: " +
            Processor.exceptionNames[cause]);
        Lib.assertNotReached("Unexpected exception");
    }

  }

  private static final int pageSize = Processor.pageSize;
  private static final char dbgProcess = 'a';
  private static final char dbgVM = 'v';
}
