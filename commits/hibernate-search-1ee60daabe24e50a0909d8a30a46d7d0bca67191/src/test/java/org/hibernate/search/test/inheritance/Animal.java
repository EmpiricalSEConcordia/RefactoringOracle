//$Id$
package org.hibernate.search.test.inheritance;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

import org.hibernate.search.annotations.DocumentId;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.Index;
import org.hibernate.search.annotations.Store;

/**
 * @author Emmanuel Bernard
 */
@Entity
public abstract class Animal extends Being {
    private Long id;
    private String name;

    @Id @GeneratedValue @DocumentId
	public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Field(index= Index.TOKENIZED, store= Store.YES)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
