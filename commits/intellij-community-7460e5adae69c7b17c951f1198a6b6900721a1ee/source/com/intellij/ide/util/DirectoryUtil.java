package com.intellij.ide.util;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.util.IncorrectOperationException;

import java.io.File;
import java.util.StringTokenizer;

public class DirectoryUtil {


  /**
   * Creates the directory with the given path via PSI, including any
   * necessary but nonexistent parent directories. Must be run in write action.
   * @param path directory path in the local file system; separators must be '/'
   * @return true if path exists or has been created as the result of this method call; false otherwise
   */
  public static PsiDirectory mkdirs(PsiManager manager, String path) throws IncorrectOperationException{
    if (File.separatorChar != '/') {
      if (path.indexOf(File.separatorChar) != -1) {
        throw new IllegalArgumentException("separators must be '/'; path is " + path);
      }
    }

    String existingPath = path;

    PsiDirectory directory = null;

    // find longest existing path
    while (existingPath.length() > 0) {
      VirtualFile file = LocalFileSystem.getInstance().findFileByPath(existingPath);
      if (file != null) {
        directory = manager.findDirectory(file);
        if (directory == null) {
          return null;
        }
        break;
      }

      if (StringUtil.endsWithChar(existingPath, '/')) {
        existingPath = existingPath.substring(0, existingPath.length() - 1);
        if (SystemInfo.isWindows && existingPath.length() == 2 && existingPath.charAt(1) == ':') {
          return null;
        }
      }

      int index = existingPath.lastIndexOf('/');
      if (index == -1) {
        // nothing to do more
        return null;
      }

      existingPath = existingPath.substring(0, index);
    }

    if (directory == null) {
      return null;
    }

    if (existingPath.equals(path)) {
      return directory;
    }

    String postfix = path.substring(existingPath.length() + 1, path.length());
    StringTokenizer tokenizer = new StringTokenizer(postfix, "/");
    while (tokenizer.hasMoreTokens()) {
      String name = tokenizer.nextToken();

      PsiDirectory subdirectory = directory.createSubdirectory(name);
      if (subdirectory == null) {
        return null;
      }

      directory = subdirectory;
    }

    return directory;
  }

}
