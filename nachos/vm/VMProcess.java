package nachos.vm;

import nachos.machine.*;
import nachos.threads.Lock;
import nachos.userprog.UserKernel;
import nachos.userprog.UserProcess;
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
    tlbBackup = new TranslationEntry[Machine.processor().getTLBSize()];
    for(int i=0;i<tlbBackup.length;i++){
      tlbBackup[i]=new TranslationEntry(0,0,false,false,false,false);
    }
  }

  /**
   * Save the state of this process in preparation for a context switch.
   * Called by <tt>UThread.saveState()</tt>.
   */
  public void saveState() {
//    super.saveState();
    for(int i=0;i<Machine.processor().getTLBSize();i++){
      tlbBackup[i]=Machine.processor().readTLBEntry(i);
      if(tlbBackup[i].valid){
        PageTable.getPageTable().updateEntry(getProcessId(), tlbBackup[i]);
      }
    }
  }

  /**
   * Restore the state of this process after a context switch. Called by
   * <tt>UThread.restoreState()</tt>.
   */
  public void restoreState() {
//    super.restoreState();
    for(int i=0;i<tlbBackup.length;i++){
      if(tlbBackup[i].valid){
        Machine.processor().writeTLBEntry(i, tlbBackup[i]);
        //can be swapped out by other processes
        TranslationEntry entry = PageTable.getPageTable().getEntry(new PageTableKey(getProcessId(), tlbBackup[i].vpn));
        if(entry != null &&  entry.valid){
          Machine.processor().writeTLBEntry(i, entry);
        } else {
          Machine.processor().writeTLBEntry(i, new TranslationEntry(0,0,false,false,false,false));
        }
      }else{
        Machine.processor().writeTLBEntry(i, new TranslationEntry(0,0,false,false,false,false));
      }
    }
  }

  public int vaddrToVpn (int vaddr) {
    return Machine.processor().pageFromAddress(vaddr);
  }

  public TranslationEntry findInPageTable (int processId, int vpn) {
    return PageTable.getPageTable().getEntry(new PageTableKey(processId, vpn));
  }

  @Override
  public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
    if(vaddr < 0) {
      return -1;
    }
    pageLock.acquire();

    int vpn = vaddrToVpn(vaddr);
    TranslationEntry entry = findInPageTable(getProcessId(), vpn);
    int ppn;
    if(entry == null || !entry.valid) {
      ppn = getAvailablePage();
      swapToMemory(ppn, vpn);
      entry = findInPageTable(getProcessId(), vpn);
    }
    entry.used = true;
    PageTable.getPageTable().replaceEntry(getProcessId(), entry);

    pageLock.release();
    return super.readVirtualMemory(vaddr, data, offset, length);
  }

  @Override
  public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
    if(vaddr < 0) {
      return -1;
    }
    pageLock.acquire();

    int vpn = vaddrToVpn(vaddr);
    TranslationEntry entry = findInPageTable(getProcessId(), vpn);
    int ppn;
    if(entry == null || !entry.valid) {
      ppn = getAvailablePage();
      swapToMemory(ppn, vpn);
      entry = findInPageTable(getProcessId(), vpn);
    }

    entry.dirty = true;
    entry.used = true;
    PageTable.getPageTable().replaceEntry(getProcessId(), entry);

    pageLock.release();
    return super.writeVirtualMemory(vaddr, data, offset, length);
  }

  @Override
  public int translateVirtualToPhysicalAddress(int virtualAddress) {
    int offset = virtualAddress & ((1<<10)-1);
//		System.out.println((1<<10)-1);
    int vpn = vaddrToVpn(virtualAddress);
//		System.out.println("vpn: " + vpn);
    int ppn = findInPageTable(getProcessId(), vpn).ppn;
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
    TranslationEntry entry = findInPageTable(getProcessId(), vpn);
    if (entry != null && entry.valid) {
      System.out.println("Invalid swap");
      return;
    }
    byte[] memory = Machine.processor().getMemory();
    byte[] pageContents = Swapper.getInstance().readFromSwapFile(getProcessId(), vpn);
    System.arraycopy(memory, ppn*pageSize, pageContents, 0, pageSize);

    TranslationEntry newEntry = new TranslationEntry(vpn, ppn, true, false, false, false);
    PageTable.getPageTable().replaceEntry(getProcessId(), newEntry);
  }

  public void memoryToSwap (int processId, int vpn) {
    TranslationEntry entry = findInPageTable(processId, vpn);
    if (entry == null || !entry.valid) {
      System.out.println("Invalid entry for swapping");
      return;
    }

    for(int i = 0; i < Machine.processor().getTLBSize(); i++) {
      TranslationEntry tlbEntry = Machine.processor().readTLBEntry(i);
      if(tlbEntry.vpn == entry.vpn && tlbEntry.ppn == entry.ppn && tlbEntry.valid) {
        PageTable.getPageTable().updateEntry(getProcessId(), tlbEntry);
        entry = PageTable.getPageTable().getEntry(new PageTableKey(getProcessId(), vpn));
        tlbEntry.valid = false;
        Machine.processor().writeTLBEntry(i, tlbEntry);
        break;
      }
    }

    if(entry.dirty) {
      byte[] memory = Machine.processor().getMemory();
      Swapper.getInstance().writeInSwapFile(getProcessId(), vpn, memory, entry.ppn * pageSize);
    }

  }

  protected int getAvailablePage() {
    int ppn = VMKernel.useNextAvailablePage();

    if(ppn == -1) {
      TransEntryWithPID replacementPage = PageTable.getPageTable().getReplacementEntry();
      ppn = replacementPage.getEntry().ppn;
      memoryToSwap(replacementPage.getProcessId(), replacementPage.getEntry().vpn);
      PageTable.getPageTable().deleteEntry(replacementPage.getProcessId(),
          replacementPage.getEntry().vpn);
    }

    return ppn;
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
        int phyPageNum = getAvailablePage();
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
      int phyPageNum = getAvailablePage();
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
    int ppn = getAvailablePage();
    System.out.println("page miss vpn: " + vpn);
    swapToMemory(vpn, ppn);
    TranslationEntry entry = findInPageTable(getProcessId(), vpn);
    handleTLBWrite(entry);
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
    TranslationEntry entry = findInPageTable(getProcessId(), vpn);

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
      case Processor.exceptionTLBMiss:
        handleTLBMiss(Machine.processor().readRegister((Processor.regBadVAddr)));
        break;

      default:
        super.handleException(cause);
    }

  }
  private TranslationEntry tlbBackup[];
  private static final Lock pageLock = new Lock();
  private static final int pageSize = Processor.pageSize;
  private static final char dbgProcess = 'a';
  private static final char dbgVM = 'v';
}
