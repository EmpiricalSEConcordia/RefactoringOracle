package com.intellij.openapi.compiler.ex;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerPaths;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.containers.OrderedSet;
import gnu.trove.TObjectHashingStrategy;

import java.io.File;
import java.util.Set;

public class CompilerPathsEx extends CompilerPaths {

  public static File getZippedOutputPath(Project project, String outputDirectoryPath) {
    final File outputDir = new File(outputDirectoryPath);
    return new File(getZipStoreDirectory(project), "_" + outputDir.getName() + Integer.toHexString(outputDirectoryPath.hashCode()) + ".zip");
  }

  public static File getZipStoreDirectory(Project project) {
    return new File(getCompilerSystemDirectory(project), ".zip");
  }

  public static class FileVisitor {
    protected void accept(final VirtualFile file, final String fileRoot, final String filePath) {
      if (file.isDirectory()) {
        acceptDirectory(file, fileRoot, filePath);
      }
      else {
        acceptFile(file, fileRoot, filePath);
      }
    }

    protected void acceptFile(VirtualFile file, String fileRoot, String filePath) {
    }

    protected void acceptDirectory(final VirtualFile file, final String fileRoot, final String filePath) {
      ProgressManager.getInstance().checkCanceled();
      final VirtualFile[] children = file.getChildren();
      for (final VirtualFile child : children) {
        final String name = child.getName();
        final String _filePath;
        final StringBuilder buf = StringBuilderSpinAllocator.alloc();
        try {
          buf.append(filePath).append("/").append(name);
          _filePath = buf.toString();
        }
        finally {
          StringBuilderSpinAllocator.dispose(buf);
        }
        accept(child, fileRoot, _filePath);
      }
    }
  }

  public static void visitFiles(final VirtualFile[] directories, final FileVisitor visitor) {
    for (final VirtualFile outputDir : directories) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          final String path = outputDir.getPath();
          visitor.accept(outputDir, path, path);
        }
      });
    }
  }

  public static String[] getOutputPaths(Module[] modules) {
    final Set<String> outputPaths = new OrderedSet<String>((TObjectHashingStrategy<String>)TObjectHashingStrategy.CANONICAL);
    for (Module module : modules) {
      final CompilerModuleExtension compilerModuleExtension = CompilerModuleExtension.getInstance(module);
      String outputPathUrl = compilerModuleExtension.getCompilerOutputUrl();
      if (outputPathUrl != null) {
        outputPaths.add(VirtualFileManager.extractPath(outputPathUrl).replace('/', File.separatorChar));
      }

      String outputPathForTestsUrl = compilerModuleExtension.getCompilerOutputUrlForTests();
      if (outputPathForTestsUrl != null) {
        outputPaths.add(VirtualFileManager.extractPath(outputPathForTestsUrl).replace('/', File.separatorChar));
      }
    }
    return ArrayUtil.toStringArray(outputPaths);
  }

  public static VirtualFile[] getOutputDirectories(final Module[] modules) {
    final Set<VirtualFile> dirs = new OrderedSet<VirtualFile>((TObjectHashingStrategy<VirtualFile>)TObjectHashingStrategy.CANONICAL);
    for (Module module : modules) {
      final VirtualFile outputDir = getModuleOutputDirectory(module, false);
      if (outputDir != null) {
        dirs.add(outputDir);
      }
      VirtualFile testsOutputDir = getModuleOutputDirectory(module, true);
      if (testsOutputDir != null) {
        dirs.add(testsOutputDir);
      }
    }
    return dirs.toArray(new VirtualFile[dirs.size()]);
  }
}
