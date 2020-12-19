package nachos.vm;

import java.util.Objects;

public class PageTableKey {
  private int processId;
  private int vpn;

  public PageTableKey(int processId, int vpn) {
    this.processId = processId;
    this.vpn = vpn;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PageTableKey that = (PageTableKey) o;
    return processId == that.processId &&
        vpn == that.vpn;
  }

  @Override
  public int hashCode() {
    return (Integer.toString(processId) + vpn).hashCode();
  }
}
