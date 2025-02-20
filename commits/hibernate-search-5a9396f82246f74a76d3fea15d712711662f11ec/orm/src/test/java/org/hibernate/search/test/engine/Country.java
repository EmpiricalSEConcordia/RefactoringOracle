/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.test.engine;

import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.NumericField;
import org.hibernate.search.annotations.Store;

import javax.persistence.Embeddable;

/**
 * author: Gustavo Fernandes
 */
@Embeddable
public class Country {

	@Field( store = Store.YES )
	@NumericField
	private double idh;

	@Field( store = Store.YES )
	private String name;

	public Country(String name, double idh) {
		this.name = name;
		this.idh = idh;
	}

	public Country() {
	}

}
