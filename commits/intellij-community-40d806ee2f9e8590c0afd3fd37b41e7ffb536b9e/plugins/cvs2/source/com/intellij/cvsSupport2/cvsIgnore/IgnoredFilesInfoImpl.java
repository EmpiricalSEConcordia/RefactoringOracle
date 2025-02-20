package com.intellij.cvsSupport2.cvsIgnore;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.cvsIgnore.IgnoredFilesInfo;
import org.netbeans.lib.cvsclient.util.SimpleStringPattern;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

/**
 * author: lesya
 */
public class IgnoredFilesInfoImpl implements IgnoredFilesInfo {

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.cvsIgnore.IgnoredFilesInfoImpl");

  private static final SimpleStringPattern[] PREDEFINED_PATTERNS = new SimpleStringPattern[]{
      new SimpleStringPattern("RCS"),
      new SimpleStringPattern("SCCS"),
      new SimpleStringPattern("CVS"),
      new SimpleStringPattern("CVS.adm"),
      new SimpleStringPattern("RCSLOG"),
      new SimpleStringPattern("cvslog.*"),
      new SimpleStringPattern("tags"),
      new SimpleStringPattern("TAGS"),
      new SimpleStringPattern(".make.state"),
      new SimpleStringPattern(".nse_depinfo"),
      new SimpleStringPattern("*~"),
      new SimpleStringPattern("#*"),
      new SimpleStringPattern(".#*"),
      new SimpleStringPattern(",*"),
      new SimpleStringPattern("_$*"),
      new SimpleStringPattern("*$"),
      new SimpleStringPattern("*.old"),
      new SimpleStringPattern("*.bak"),
      new SimpleStringPattern("*.BAK"),
      new SimpleStringPattern("*.orig"),
      new SimpleStringPattern("*.rej"),
      new SimpleStringPattern(".del-*"),
      new SimpleStringPattern("*.a"),
      new SimpleStringPattern("*.olb"),
      new SimpleStringPattern("*.o"),
      new SimpleStringPattern("*.obj"),
      new SimpleStringPattern("*.so"),
      new SimpleStringPattern("*.exe"),
      new SimpleStringPattern("*.Z"),
      new SimpleStringPattern("*.elc"),
      new SimpleStringPattern("*.ln"),
      new SimpleStringPattern("core")
  };

  public static IgnoredFilesInfo EMPTY_FILTER = new IgnoredFilesInfoImpl(){
    public boolean shouldBeIgnored(String fileName) {
      if (checkPatterns(CvsEntriesManager.getInstance().getUserdIgnores().getPatterns(), fileName)) return true;
      if (checkPatterns(PREDEFINED_PATTERNS, fileName)) return true;
      return false;
    }
  };

  private List myPatterns = null;

  public static IgnoredFilesInfo createForFile(File file){
    if (!file.isFile()) return EMPTY_FILTER;
    return new IgnoredFilesInfoImpl(file);
  }

  private IgnoredFilesInfoImpl() { }

  public static ArrayList getPattensFor(File cvsIgnoreFile){
    ArrayList result = new ArrayList();

    if (!cvsIgnoreFile.exists()) return result;

    try {
      BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(cvsIgnoreFile)));
      try{
      String line;
        while((line = reader.readLine()) != null){
          StringTokenizer stringTokenizer = new StringTokenizer(line, " ");
          while (stringTokenizer.hasMoreTokens()){
            result.add(new SimpleStringPattern(stringTokenizer.nextToken()));
          }

        }
      } catch (Exception ex){

      } finally{
        try {
          reader.close();
        } catch (java.io.IOException e) {

        }
      }

    } catch (java.io.FileNotFoundException e) {
      LOG.error(e);
    }

    return result;
  }

  private IgnoredFilesInfoImpl(File cvsIgnoreFile){
    myPatterns = getPattensFor(cvsIgnoreFile);
  }

  public boolean shouldBeIgnored(String fileName) {
    if (EMPTY_FILTER.shouldBeIgnored(fileName)) return true;
    return checkPatterns(myPatterns, fileName);
  }

  protected static boolean checkPatterns(List patterns, String fileName) {
    for (Iterator iterator = patterns.iterator(); iterator.hasNext();) {
      SimpleStringPattern simpleStringPattern = (SimpleStringPattern) iterator.next();
      if (simpleStringPattern.doesMatch(fileName)) return true;
    }
    return false;
  }

  protected static boolean checkPatterns(SimpleStringPattern[] patterns, String fileName) {
    for (int i = 0; i < patterns.length; i++) {
      if (patterns[i].doesMatch(fileName)) return true;
    }
    return false;
  }

}
