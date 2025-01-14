//$Id$
package org.hibernate.search.bridge;

import org.apache.lucene.document.Document;

/**
 * Link between a java property and a Lucene Document
 * Usually a Java property will be linked to a Document Field.
 * <p/>
 * All implementations need to be threadsafe.
 *
 * @author Emmanuel Bernard
 */
public interface FieldBridge {

	/**
	 * Manipulate the document to index the given value.
	 * <p/>
	 * A common implementation is to add a Field with the given {@code name} to {@code document} following
	 * the parameters {@code luceneOptions} if the {@code value} is not {@code null}.
	 *
	 * @param name The field to add to the Lucene document
	 * @param value The actual value to index
	 * @param document The Lucene document into which we want to index the value.
	 * @param luceneOptions Contains the parameters used for adding {@code value} to
	 * the Lucene document.
	 */
	void set(String name, Object value, Document document, LuceneOptions luceneOptions);
}
