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
package org.jboss.envers.configuration.metadata;

import javax.persistence.Version;
import javax.persistence.MapKey;

import org.jboss.envers.configuration.GlobalConfiguration;
import org.jboss.envers.tools.reflection.YClass;
import org.jboss.envers.tools.reflection.YProperty;
import org.jboss.envers.tools.reflection.YReflectionManager;
import org.jboss.envers.*;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.MappingException;

import java.lang.annotation.Annotation;

/**
 * A helper class to read versioning meta-data from annotations on a persistent class.
 * @author Adam Warski (adam at warski dot org)
 * @author Sebastian Komander
 */
public final class AnnotationsMetadataReader {
    private final GlobalConfiguration globalCfg;
    private final YReflectionManager reflectionManager;
    private final PersistentClass pc;

    /**
     * This object is filled with information read from annotations and returned by the <code>getVersioningData</code>
     * method.
     */
    private final PersistentClassVersioningData versioningData;

    public AnnotationsMetadataReader(GlobalConfiguration globalCfg, YReflectionManager reflectionManager,
                                     PersistentClass pc) {
        this.globalCfg = globalCfg;
        this.reflectionManager = reflectionManager;
        this.pc = pc;

        versioningData = new PersistentClassVersioningData();
    }

    private void addPropertyVersioned(YProperty property) {
        Versioned ver = property.getAnnotation(Versioned.class);
        if (ver != null) {
            versioningData.propertyStoreInfo.propertyStores.put(property.getName(), ver.modStore());
        }
    }

    private void addPropertyMapKey(YProperty property) {
        MapKey mapKey = property.getAnnotation(MapKey.class);
        if (mapKey != null) {
            versioningData.mapKeys.put(property.getName(), mapKey.name());
        }
    }

    private void addPropertyUnversioned(YProperty property) {
        // check if a property is declared as unversioned to exclude it
        // useful if a class is versioned but some properties should be excluded
        Unversioned unVer = property.getAnnotation(Unversioned.class);
        if (unVer != null) {
            versioningData.unversionedProperties.add(property.getName());
        } else {
            // if the optimistic locking field has to be unversioned and the current property
            // is the optimistic locking field, add it to the unversioned properties list
            if (globalCfg.isUnversionedOptimisticLockingField()) {
                Version jpaVer = property.getAnnotation(Version.class);
                if (jpaVer != null) {
                    versioningData.unversionedProperties.add(property.getName());
                }
            }
        }
    }

    private void addPropertyJoinTables(YProperty property) {
        VersionsJoinTable joinTable = property.getAnnotation(VersionsJoinTable.class);
        if (joinTable != null) {
            versioningData.versionsJoinTables.put(property.getName(), joinTable);
        }
    }

    private void addFromProperties(Iterable<YProperty> properties) {
        for (YProperty property : properties) {
            addPropertyVersioned(property);
            addPropertyUnversioned(property);
            addPropertyJoinTables(property);
            addPropertyMapKey(property);
        }
    }

    private void addPropertiesFromClass(YClass clazz)  {
        YClass superclazz = clazz.getSuperclass();
        if (!"java.lang.Object".equals(superclazz.getName())) {
            addPropertiesFromClass(superclazz);
        }

        addFromProperties(clazz.getDeclaredProperties("field"));
        addFromProperties(clazz.getDeclaredProperties("property"));
    }

    private void addDefaultVersioned(YClass clazz) {
        Versioned defaultVersioned = clazz.getAnnotation(Versioned.class);

        if (defaultVersioned != null) {
            versioningData.propertyStoreInfo.defaultStore = defaultVersioned.modStore();
        }
    }

    private void addVersionsTable(YClass clazz) {
        VersionsTable versionsTable = clazz.getAnnotation(VersionsTable.class);
        if (versionsTable != null) {
            versioningData.versionsTable = versionsTable;
        } else {
            versioningData.versionsTable = getDefaultVersionsTable();
        }
    }

    private void addVersionsSecondaryTables(YClass clazz) {
        // Getting information on secondary tables
        SecondaryVersionsTable secondaryVersionsTable1 = clazz.getAnnotation(SecondaryVersionsTable.class);
        if (secondaryVersionsTable1 != null) {
            versioningData.secondaryTableDictionary.put(secondaryVersionsTable1.secondaryTableName(),
                    secondaryVersionsTable1.secondaryVersionsTableName());
        }

        SecondaryVersionsTables secondaryVersionsTables = clazz.getAnnotation(SecondaryVersionsTables.class);
        if (secondaryVersionsTables != null) {
            for (SecondaryVersionsTable secondaryVersionsTable2 : secondaryVersionsTables.value()) {
                versioningData.secondaryTableDictionary.put(secondaryVersionsTable2.secondaryTableName(),
                        secondaryVersionsTable2.secondaryVersionsTableName());
            }
        }
    }

    public PersistentClassVersioningData getVersioningData() {
        if (pc.getClassName() == null) {
            return versioningData;
        }

        try {
            YClass clazz = reflectionManager.classForName(pc.getClassName(), this.getClass());

            addDefaultVersioned(clazz);
            addPropertiesFromClass(clazz);
            addVersionsTable(clazz);
            addVersionsSecondaryTables(clazz);
        } catch (ClassNotFoundException e) {
            throw new MappingException(e);
        }

        return versioningData;
    }

    private VersionsTable getDefaultVersionsTable() {
        return new VersionsTable() {
            public String value() { return ""; }
            public String schema() { return ""; }
            public String catalog() { return ""; }
            public Class<? extends Annotation> annotationType() { return this.getClass(); }
        };
    }
}
