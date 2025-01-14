package org.elasticsearch.bootstrap;

/**
 * Same as {@link Elasticsearch} just runs it in the foreground by default (does not close
 * sout and serr).
 */
public class ElasticsearchF {

    public static void close(String[] args) {
        Bootstrap.close(args);
    }

    public static void main(String[] args) {
        System.setProperty("es.foreground", "yes");
        Bootstrap.main(args);
    }
}
