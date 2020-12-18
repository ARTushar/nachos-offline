package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;

import java.io.EOFException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see	nachos.vm.VMProcess
 * @see	nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
		int numPhysPages = Machine.processor().getNumPhysPages();
		pageTable = new TranslationEntry[numPhysPages];
		for (int i=0; i<numPhysPages; i++)
			pageTable[i] = new TranslationEntry(i,i, true,false,false,false);

//		fileRead = UserKernel.console.openForReading();
//		fileWrite = UserKernel.console.openForWriting();
	}

	/**
	 * Allocate and return a new process of the correct class. The class name
	 * is specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 *
	 * @return	a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
		return (UserProcess)Lib.constructObject(Machine.getProcessClassName());
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 *
	 * @param	name	the name of the file containing the executable.
	 * @param	args	the arguments to pass to the executable.
	 * @return	<tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

		thread = new UThread(this);
    thread.setName(name).fork();

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}


	public int translateVirtualToPhysicalAddress(int virtualAddress) {
		int offset = virtualAddress & ((1<<10)-1);
//		System.out.println((1<<10)-1);
		int vpn = (virtualAddress>>>10);
//		System.out.println("vpn: " + vpn);
		int ppn = pageTable[vpn].ppn;
//		System.out.println("ppn: "+ppn);
//		System.out.println("paddr: "+(ppn<<10)+offset);
		return (ppn<<10)+offset;
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read
	 * at most <tt>maxLength + 1</tt> bytes from the specified address, search
	 * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 *
	 * @param	vaddr	the starting virtual address of the null-terminated
	 *			string.
	 * @param	maxLength	the maximum number of characters in the string,
	 *				not including the null terminator.
	 * @return	the string read, or <tt>null</tt> if no null terminator was
	 *		found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength+1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length=0; length<bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 *
	 * @param	vaddr	the first byte of virtual memory to read.
	 * @param	data	the array where the data will be stored.
	 * @return	the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no
	 * data could be copied).
	 *
	 * @param	vaddr	the first byte of virtual memory to read.
	 * @param	data	the array where the data will be stored.
	 * @param	offset	the first byte to write in the array.
	 * @param	length	the number of bytes to transfer from virtual memory to
	 *			the array.
	 * @return	the number of bytes successfully transferred.
	 */
	@SuppressWarnings("Duplicates")
	public int readVirtualMemory(int vaddr, byte[] data, int offset,
															 int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);
//		System.out.println("Hi i am in read virtual memory");
		byte[] memory = Machine.processor().getMemory();

		// translating virtual address to physical address
		if (vaddr < 0 || vaddr >= memory.length)
			return 0;

		int paddr = translateVirtualToPhysicalAddress(vaddr);
//		System.out.println("vaddr: " + vaddr + " padd: " + paddr);
		if (paddr<0 || paddr >= memory.length) {
			return 0;
		}

		int amount = Math.min(length, memory.length-paddr);
		System.arraycopy(memory, paddr, data, offset, amount);

		return amount;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory.
	 * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 *
	 * @param	vaddr	the first byte of virtual memory to write.
	 * @param	data	the array containing the data to transfer.
	 * @return	the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no
	 * data could be copied).
	 *
	 * @param	vaddr	the first byte of virtual memory to write.
	 * @param	data	the array containing the data to transfer.
	 * @param	offset	the first byte to transfer from the array.
	 * @param	length	the number of bytes to transfer from the array to
	 *			virtual memory.
	 * @return	the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset,
																int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);
//		System.out.println("Hi i am in write virtual memory");

		byte[] memory = Machine.processor().getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		if (vaddr < 0 || vaddr >= memory.length)
			return 0;

		int paddr = translateVirtualToPhysicalAddress(vaddr);
//		System.out.println("vaddr: " + vaddr + " padd: " + paddr);
		if (paddr<0 || paddr >= memory.length) {
			return 0;
		}
		int amount = Math.min(length, memory.length-paddr);
		System.arraycopy(data, offset, memory, paddr, amount);

		return amount;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 *
	 * @param	name	the name of the file containing the executable.
	 * @param	args	the arguments to pass to the executable.
	 * @return	<tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		System.out.println("Load");

		try {
			coff = new Coff(executable);
		}
		catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s=0; s<coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i=0; i<args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages*pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		System.out.println("before load section");
		if (!loadSections())
			return false;

		System.out.println("after load section");

		// store arguments in last page
		int entryOffset = (numPages-1)*pageSize;
		int stringOffset = entryOffset + args.length*4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i=0; i<argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset,stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) ==
					argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset,new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be
	 * run (this is the last step in process initialization that can fail).
	 *
	 * @return	<tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		System.out.println("Pages: " + numPages + " available: " + UserKernel.availablePageList.size());
		if (numPages > UserKernel.availablePageList.size()){
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}

		// load sections
		int totalAdded = 0;
		for (int s=0; s<coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i=0; i<section.getLength(); i++) {
				int vpn = section.getFirstVPN()+i;
				// mapping virtual to phyvpnsical address
				UserKernel.lock.acquire();
				int phyPageNum = UserKernel.useNextAvailablePage();
				UserKernel.lock.release();
				pageTable[vpn].ppn = phyPageNum;
				if(section.isReadOnly()) pageTable[vpn].readOnly = true;
				section.loadPage(i, phyPageNum);
				totalAdded++;
			}
		}

		for(int i = totalAdded; i < numPages; i++){
			// mapping virtual to physical address
			UserKernel.lock.acquire();
			int phyPageNum = UserKernel.useNextAvailablePage();
			UserKernel.lock.release();
			pageTable[i].ppn = phyPageNum;
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		int totalDeleted = 0;
		for (int s=0; s<coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			for (int i=0; i<section.getLength(); i++) {
				int vpn = section.getFirstVPN()+i;
				// mapping virtual to physical address
				int phyPageNum = pageTable[vpn].ppn;
				pageTable[vpn].ppn = -1;
				pageTable[vpn].readOnly = false;
				UserKernel.lock.acquire();
				UserKernel.addNewAvailablePage(phyPageNum);
				UserKernel.lock.release();
				totalDeleted++;
			}
		}

		for(int i = totalDeleted; i < numPages; i++){
			int phyPageNum = pageTable[i].ppn;
			pageTable[i].ppn = -1;
			pageTable[i].readOnly = false;
			UserKernel.lock.acquire();
			UserKernel.addNewAvailablePage(phyPageNum);
			UserKernel.lock.release();
		}
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of
	 * the stack, set the A0 and A1 registers to argc and argv, respectively,
	 * and initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i=0; i<processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {
		System.out.println("halting");
		if(UserKernel.currentProcess().parentProcess != null) return -1;
		unloadSections();
		fileRead.close();
		fileWrite.close();
		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		System.out.println("halted");
		return 0;
	}

	private int handleRead(int fd, int virtualAddress, int size) {
		if(fd != 0 || size <= 0) return -1;
//		System.out.println("Handle Read");

		byte[] data = new byte[size];
		readSemaphore.P();
		fileRead.read(data, 0, size);
		readSemaphore.V();
		return writeVirtualMemory(virtualAddress, data);
  }

  private int handleWrite(int fd, int virtualAdress, int size){
		if(fd != 1 || size <= 0) return -1;
		byte[] data = new byte[size];
		int length = readVirtualMemory(virtualAdress, data);
//		if(length == 0) return 0;
		writeSemaphore.P();
		length = fileWrite.write(data, 0, length);
		writeSemaphore.V();
		return length;
	}


	private void handleExit(int status){
		unloadSections();
		if(parentProcess != null){
			//System.out.println("process exiting : " + processId);
			parentProcess.childProcesesStatus.replace(processId, status);
			//System.out.println("length before : " + parentProcess.childProcesses.size());
			parentProcess.childProcesses.remove(this);
			//System.out.println("length before : " + parentProcess.childProcesses.size());
		} else{
			fileWrite.close();
			fileRead.close();
		}
		for(int i = 0; i < childProcesses.size(); i++){
			childProcesses.get(i).parentProcess = null;
		}

		if(parentProcess == null) Kernel.kernel.terminate();
		KThread.currentThread().finish();
	}

	private int handleExec(int fileVirtualAddress, int argc, int argvVirtualAdress){
		String fileName = readVirtualMemoryString(fileVirtualAddress, 128);

		String[] argvs = new String[argc];
		int tempAddress = argvVirtualAdress;
		System.out.println("argc: " + argc + " fileName: " + fileName);
		for(int i = 0; i < argc; i++){
		  byte[] buffer = new byte[4];
		  int readLength = readVirtualMemory(tempAddress + i * 4, buffer);
			if(readLength != 4)	 return -1;
			int argvAddress = Lib.bytesToInt(buffer, 0);
			argvs[i] = readVirtualMemoryString(argvAddress, 128);
		}

		UserProcess process = newUserProcess();
		process.parentProcess = this;
		processSemaphore.P();
		process.processId = totalProcesses + 1;
		totalProcesses++;
		childProcesesStatus.put(totalProcesses, 0);
		processSemaphore.V();
		childProcesses.add(process);
		if(process.execute(fileName, argvs)){
			return process.processId;
		}
		return -1;
	}

	private int handleJoin(int processId, int virtualAdressStatus){
		if(processId < 0 || virtualAdressStatus < 0){
			return -1;
		}

//		UserProcess child = null;
//		for(int i = 0; i < childProcesses.size(); i++){
////			System.out.println("join  child process : " + childProcesses.get(i).processId);
//			if(childProcesses.get(i).processId == processId){
//				System.out.println("join  child process : " + processId);
//				child = childProcesses.get(i);
//				break;
//			}
//		}
//
//		if(child == null) {
//			return -1;
//		}
//
//		child.thread.join();
//
//		child.parentProcess = null;
//		childProcesses.remove(child);
//
//		Integer status = childProcesesStatus.get(child.processId);
//		if(status == null) {
//			return 0;
//		} else {
//			byte[] buffer=new byte[4];
//			buffer=Lib.bytesFromInt(status);
//			int count=writeVirtualMemory(virtualAdressStatus, buffer);
//			if(count==4){
//				return 1;
//			}else{
//				System.out.println("handleJoin:Write status failed");
//				return 0;
//			}
//		}

	  UserProcess child = null;
	  for(int i = 0; i < childProcesses.size(); i++){
//			System.out.println("join  child process : " + childProcesses.get(i).processId);
	  	if(childProcesses.get(i).processId == processId){
			System.out.println("join  child process : " + processId);
	  		child = childProcesses.get(i);
	  		break;
			}
		}

	  if(child == null){
			System.out.println("hi am here");
	  	return -1;
		}
		System.out.println("hi im joining the child");
	  child.thread.join();
		System.out.println("hi joining the child done");
	  int status = childProcesesStatus.get(child.processId);
	  byte[] statusByte = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
				.putInt(status).array();
		System.out.println("writing the status in join");
	  writeVirtualMemory(virtualAdressStatus, statusByte);
		System.out.println("writing the status in join done");

	  if(status == -1) return 0;
	  return 1;
	}


	private static final int
			syscallHalt = 0,
			syscallExit = 1,
			syscallExec = 2,
			syscallJoin = 3,
			syscallCreate = 4,
			syscallOpen = 5,
			syscallRead = 6,
			syscallWrite = 7,
			syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 *
	 * <table>
	 * <tr><td>syscall#</td><td>syscall prototype</td></tr>
	 * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
	 * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
	 * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td></tr>
	 * <tr><td>3</td><td><tt>int  join(int pid, int *status);</tt></td></tr>
	 * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
	 * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
	 * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
	 *								</tt></td></tr>
	 * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
	 *								</tt></td></tr>
	 * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
	 * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
	 * </table>
	 *
	 * @param	syscall	the syscall number.
	 * @param	a0	the first syscall argument.
	 * @param	a1	the second syscall argument.
	 * @param	a2	the third syscall argument.
	 * @param	a3	the fourth syscall argument.
	 * @return	the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
//		System.out.println("syscall : " + syscall);
		switch (syscall) {
			case syscallHalt:
				return handleHalt();

			case syscallRead:
				return handleRead(a0, a1, a2);

			case syscallWrite:
				return handleWrite(a0, a1, a2);

			case syscallExit:
				handleExit(a0);
				return 1;

			case syscallExec:
				return handleExec(a0, a1, a2);

			case syscallJoin:
				return handleJoin(a0, a1);

			default:
				unloadSections();
				Lib.debug(dbgProcess, "Unknown syscall " + syscall);
				Lib.assertNotReached("Unknown system call!");
		}
		return 0;
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

			default:
			  if(parentProcess != null){
			  	parentProcess.childProcesesStatus.replace(processId, -1);
				}
				Lib.debug(dbgProcess, "Unexpected exception: " +
						Processor.exceptionNames[cause]);
				Lib.assertNotReached("Unexpected exception");
		}
	}

	public int getProcessId() {
		return processId;
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;
	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	private int initialPC, initialSP;
	private int argc, argv;

	private static final int pageSize = Processor.pageSize;
	private static final char dbgProcess = 'a';
	private ArrayList<UserProcess> childProcesses = new ArrayList<>();
	public HashMap<Integer, Integer> childProcesesStatus = new HashMap<>();
	protected UserProcess parentProcess;
	private int processId;
	private static int totalProcesses= 0;
	private UThread thread;
	private static Semaphore processSemaphore = new Semaphore(1);
	private static Semaphore readSemaphore = new Semaphore(1);
	private static Semaphore writeSemaphore = new Semaphore(1);
	private static OpenFile fileRead  = UserKernel.console.openForReading();
	private static OpenFile fileWrite = UserKernel.console.openForWriting();
}
