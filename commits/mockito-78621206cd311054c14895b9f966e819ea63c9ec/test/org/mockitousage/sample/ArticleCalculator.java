/*
 * Copyright (c) 2007 Mockito contributors 
 * This program is made available under the terms of the MIT License.
 */
package org.mockitousage.sample;

public interface ArticleCalculator {
    int countArticles(String newspaper);
    int countArticlesInPolish(String newspaper);
    int countNumberOfRelatedArticles(Article article);
}