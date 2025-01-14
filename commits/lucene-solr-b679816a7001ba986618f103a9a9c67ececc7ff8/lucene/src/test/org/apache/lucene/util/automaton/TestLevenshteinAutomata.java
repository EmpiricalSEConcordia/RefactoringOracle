package org.apache.lucene.util.automaton;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.BasicAutomata;
import org.apache.lucene.util.automaton.BasicOperations;
import org.apache.lucene.util.automaton.MinimizationOperations;

public class TestLevenshteinAutomata extends LuceneTestCase {
 
  public void testLev0() throws Exception {
    assertLev("", 0);
    assertCharVectors(0);
  }
  
  public void testLev1() throws Exception {
    assertLev("", 1);
    assertCharVectors(1);
  }
  
  public void testLev2() throws Exception {
    assertLev("", 2);
    assertCharVectors(2);
  }
  
  /** 
   * Tests all possible characteristic vectors for some n
   * This exhaustively tests the parametric transitions tables.
   */
  private void assertCharVectors(int n) {
    int k = 2*n + 1;
    // use k + 2 as the exponent: the formula generates different transitions
    // for w, w-1, w-2
    int limit = (int) Math.pow(2, k + 2);
    for (int i = 0; i < limit; i++) {
      String encoded = Integer.toString(i, 2);
      assertLev(encoded, n);
    }
  }

  /**
   * Builds a DFA for some string, and checks all Lev automata
   * up to some maximum distance.
   */
  private void assertLev(String s, int maxDistance) {
    LevenshteinAutomata builder = new LevenshteinAutomata(s);
    Automaton automata[] = new Automaton[maxDistance + 1];
    for (int n = 0; n < automata.length; n++) {
      automata[n] = builder.toAutomaton(n);
      assertNotNull(automata[n]);
      assertTrue(automata[n].isDeterministic());
      assertTrue(SpecialOperations.isFinite(automata[n]));
      // check that the dfa for n-1 accepts a subset of the dfa for n
      if (n > 0) {
        assertTrue(automata[n-1].subsetOf(automata[n]));
        assertNotSame(automata[n-1], automata[n]);
      }
      // special checks for specific n
      switch(n) {
        case 0:
          // easy, matches the string itself
          assertEquals(BasicAutomata.makeString(s), automata[0]);
          break;
        case 1:
          // generate a lev1 naively, and check the accepted lang is the same.
          assertEquals(naiveLev1(s), automata[1]);
          break;
        default:
          assertBruteForce(s, automata[n], n);
          break;
      }
    }
  }
  
  /**
   * Return an automaton that accepts all 1-character insertions, deletions, and
   * substitutions of s.
   */
  private Automaton naiveLev1(String s) {
    Automaton a = BasicAutomata.makeString(s);
    a = BasicOperations.union(a, insertionsOf(s));
    MinimizationOperations.minimize(a);
    a = BasicOperations.union(a, deletionsOf(s));
    MinimizationOperations.minimize(a);
    a = BasicOperations.union(a, substitutionsOf(s));
    MinimizationOperations.minimize(a);
    
    return a;
  }
  
  /**
   * Return an automaton that accepts all 1-character insertions of s (inserting
   * one character)
   */
  private Automaton insertionsOf(String s) {
    List<Automaton> list = new ArrayList<Automaton>();
    
    for (int i = 0; i <= s.length(); i++) {
      Automaton a = BasicAutomata.makeString(s.substring(0, i));
      a = BasicOperations.concatenate(a, BasicAutomata.makeAnyChar());
      a = BasicOperations.concatenate(a, BasicAutomata.makeString(s
          .substring(i)));
      list.add(a);
    }
    
    Automaton a = BasicOperations.union(list);
    MinimizationOperations.minimize(a);
    return a;
  }
  
  /**
   * Return an automaton that accepts all 1-character deletions of s (deleting
   * one character).
   */
  private Automaton deletionsOf(String s) {
    List<Automaton> list = new ArrayList<Automaton>();
    
    for (int i = 0; i < s.length(); i++) {
      Automaton a = BasicAutomata.makeString(s.substring(0, i));
      a = BasicOperations.concatenate(a, BasicAutomata.makeString(s
          .substring(i + 1)));
      a.expandSingleton();
      list.add(a);
    }
    
    Automaton a = BasicOperations.union(list);
    MinimizationOperations.minimize(a);
    return a;
  }
  
  /**
   * Return an automaton that accepts all 1-character substitutions of s
   * (replacing one character)
   */
  private Automaton substitutionsOf(String s) {
    List<Automaton> list = new ArrayList<Automaton>();
    
    for (int i = 0; i < s.length(); i++) {
      Automaton a = BasicAutomata.makeString(s.substring(0, i));
      a = BasicOperations.concatenate(a, BasicAutomata.makeAnyChar());
      a = BasicOperations.concatenate(a, BasicAutomata.makeString(s
          .substring(i + 1)));
      list.add(a);
    }
    
    Automaton a = BasicOperations.union(list);
    MinimizationOperations.minimize(a);
    return a;
  }
  
  private void assertBruteForce(String input, Automaton dfa, int distance) {
    RunAutomaton ra = new RunAutomaton(dfa);
    int maxLen = input.length() + distance + 1;
    int maxNum = (int) Math.pow(2, maxLen);
    for (int i = 0; i < maxNum; i++) {
      String encoded = Integer.toString(i, 2);
      boolean accepts = ra.run(encoded);
      if (accepts) {
        assertTrue(getDistance(input, encoded) <= distance);
      } else {
        assertTrue(getDistance(input, encoded) > distance);
      }
    }
  }
  
  //*****************************
  // Compute Levenshtein distance: see org.apache.commons.lang.StringUtils#getLevenshteinDistance(String, String)
  //*****************************
  private int getDistance (String target, String other) {
    char[] sa;
    int n;
    int p[]; //'previous' cost array, horizontally
    int d[]; // cost array, horizontally
    int _d[]; //placeholder to assist in swapping p and d
    
      /*
         The difference between this impl. and the previous is that, rather
         than creating and retaining a matrix of size s.length()+1 by t.length()+1,
         we maintain two single-dimensional arrays of length s.length()+1.  The first, d,
         is the 'current working' distance array that maintains the newest distance cost
         counts as we iterate through the characters of String s.  Each time we increment
         the index of String t we are comparing, d is copied to p, the second int[].  Doing so
         allows us to retain the previous cost counts as required by the algorithm (taking
         the minimum of the cost count to the left, up one, and diagonally up and to the left
         of the current cost count being calculated).  (Note that the arrays aren't really
         copied anymore, just switched...this is clearly much better than cloning an array
         or doing a System.arraycopy() each time  through the outer loop.)

         Effectively, the difference between the two implementations is this one does not
         cause an out of memory condition when calculating the LD over two very large strings.
       */

      sa = target.toCharArray();
      n = sa.length;
      p = new int[n+1]; 
      d = new int[n+1]; 
    
      final int m = other.length();
      if (n == 0 || m == 0) {
        if (n == m) {
          return 0;
        }
        else {
          return Math.max(n, m);
        }
      } 


      // indexes into strings s and t
      int i; // iterates through s
      int j; // iterates through t

      char t_j; // jth character of t

      int cost; // cost

      for (i = 0; i<=n; i++) {
          p[i] = i;
      }

      for (j = 1; j<=m; j++) {
          t_j = other.charAt(j-1);
          d[0] = j;

          for (i=1; i<=n; i++) {
              cost = sa[i-1]==t_j ? 0 : 1;
              // minimum of cell to the left+1, to the top+1, diagonally left and up +cost
              d[i] = Math.min(Math.min(d[i-1]+1, p[i]+1),  p[i-1]+cost);
          }

          // copy current distance counts to 'previous row' distance counts
          _d = p;
          p = d;
          d = _d;
      }

      // our last action in the above loop was to switch d and p, so p now
      // actually has the most recent cost counts
      return Math.abs(p[n]);
  }
}
