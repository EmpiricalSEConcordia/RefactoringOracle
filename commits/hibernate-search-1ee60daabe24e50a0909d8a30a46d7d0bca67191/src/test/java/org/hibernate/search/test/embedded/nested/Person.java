// $Id:$
/*
* JBoss, Home of Professional Open Source
* Copyright 2008, Red Hat Middleware LLC, and individual contributors
* by the @authors tag. See the copyright.txt in the distribution for a
* full listing of individual contributors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
* http://www.apache.org/licenses/LICENSE-2.0
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.hibernate.search.test.embedded.nested;

import java.util.ArrayList;
import java.util.List;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToMany;

import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.IndexedEmbedded;

/**
 * @author Hardy Ferentschik
 */
@Entity
@Indexed
public class Person {
	@Id
	@GeneratedValue
	private long id;

	String name;

	@IndexedEmbedded
	@ManyToMany(cascade = { CascadeType.ALL })
	private List<Place> placesVisited;

	private Person() {
		placesVisited = new ArrayList<Place>( 0 );
	}

	public Person(String name) {
		this();
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public List<Place> getPlacesVisited() {
		return placesVisited;
	}

	public void addPlaceVisited(Place place) {
		placesVisited.add( place );
	}

	public long getId() {
		return id;
	}
}
