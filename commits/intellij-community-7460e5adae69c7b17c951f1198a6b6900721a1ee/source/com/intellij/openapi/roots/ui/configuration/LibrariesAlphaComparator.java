package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Comparator;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 14, 2004
 */
public class LibrariesAlphaComparator implements Comparator<Library> {
  public static LibrariesAlphaComparator INSTANCE = new LibrariesAlphaComparator();

  public int compare(Library library1, Library library2) {
    String name1 = library1.getName();
    if (name1 != null && name1.length() == 0) {
      name1 = null;
    }
    String name2 = library2.getName();
    if (name2 != null && name2.length() == 0) {
      name2 = null;
    }
    if (name1 == null && name2 == null) {
      final VirtualFile[] files1 = library1.getFiles(OrderRootType.CLASSES);
      final VirtualFile[] files2 = library2.getFiles(OrderRootType.CLASSES);
      name1 = files1.length > 0? files1[0].getName() : null;
      name2 = files2.length > 0? files2[0].getName() : null;
    }
    return compareNames(name1, name2);
  }

  public int compareNames(String name1, String name2) {
    if (name1 == null && name2 == null) {
      return 0;
    }
    else if (name1 == null) {
      return -1;
    }
    else if (name2 == null) {
      return +1;
    }
    else {
      return name1.compareToIgnoreCase(name2);
    }
  }
}
