package nachos.vm;

import java.util.Objects;

public class CoffPagePair {
  private int section;
  private int page;

  public CoffPagePair(int section, int page) {
    this.section = section;
    this.page = page;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CoffPagePair that = (CoffPagePair) o;
    return section == that.section &&
        page == that.page;
  }

  @Override
  public int hashCode() {
    return Objects.hash(section, page);
  }

  public int getSection() {
    return section;
  }

  public int getPage() {
    return page;
  }
}
