/*
 * Envers. http://www.jboss.org/envers
 *
 * Copyright 2008  Red Hat Middleware, LLC. All rights reserved.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT A WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License, v.2.1 along with this distribution; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 *
 * Red Hat Author(s): Adam Warski
 */
package org.jboss.envers.entities.mapper;

import org.jboss.envers.reader.VersionsReaderImplementor;
import org.jboss.envers.configuration.VersionsConfiguration;
import org.hibernate.collection.PersistentCollection;

import java.util.Map;
import java.util.List;
import java.io.Serializable;

/**
 * @author Adam Warski (adam at warski dot org)
 */
public interface PropertyMapper {
    /**
     * Maps properties to the given map, basing on differences between properties of new and old objects.
     * @param data Data to map to.
     * @param newObj New state of the entity.
     * @param oldObj Old state of the entity.
     * @return True if there are any differences between the states represented by newObj and oldObj.
     */
    boolean mapToMapFromEntity(Map<String, Object> data, Object newObj, Object oldObj);

    /**
     * Maps properties from the given map to the given object.
     * @param verCfg Versions configuration.
     * @param obj Object to map to.
     * @param data Data to map from.
     * @param primaryKey Primary key of the object to which we map (for relations)
     * @param versionsReader VersionsReader for reading relations
     * @param revision Revision at which the object is read, for reading relations
     */
    void mapToEntityFromMap(VersionsConfiguration verCfg, Object obj, Map data, Object primaryKey,
                            VersionsReaderImplementor versionsReader, Number revision);

    /**
     * Maps collection changes
     * @param referencingPropertyName Name of the field, which holds the collection in the entity.
     * @param newColl New collection, after updates.
     * @param oldColl Old collection, before updates.
     * @param id Id of the object owning the collection.
     * @return List of changes that need to be performed on the persistent store.
     */
    List<PersistentCollectionChangeData> mapCollectionChanges(String referencingPropertyName,
                                                              PersistentCollection newColl,
                                                              Serializable oldColl, Serializable id);
}
