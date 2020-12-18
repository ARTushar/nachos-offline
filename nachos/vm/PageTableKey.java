package nachos.vm;

public class PageTableKey {
  private int processId;
  private int vpn;

  public PageTableKey(int processId, int vpn) {
    this.processId = processId;
    this.vpn = vpn;
  }

  @Override
  public int hashCode() {
    return (Integer.toString(processId)+ Integer.toString(vpn)).hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if(obj == null) return false;
    PageTableKey comparedKey = (PageTableKey) obj;
    return processId == comparedKey.processId && vpn == comparedKey.vpn;
  }
}
