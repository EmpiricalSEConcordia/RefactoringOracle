/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2008, Red Hat Middleware LLC or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Middleware LLC.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.hibernate.envers;

import java.util.Date;
import java.util.List;

import org.hibernate.envers.exception.NotAuditedException;
import org.hibernate.envers.exception.RevisionDoesNotExistException;
import org.hibernate.envers.query.AuditQueryCreator;

/**
 * @author Adam Warski (adam at warski dot org)
 */
public interface AuditReader {
    /**
     * Find an entity by primary key at the given revision.
     * @param cls Class of the entity.
     * @param primaryKey Primary key of the entity.
     * @param revision Revision in which to get the entity.
     * @return The found entity instance at the given revision (its properties may be partially filled
     * if not all properties are versioned) or null, if an entity with that id didn't exist at that
     * revision.
     * @throws IllegalArgumentException If cls or primaryKey is null or revision is less or equal to 0.
     * @throws NotAuditedException When entities of the given class are not audited.
     * @throws IllegalStateException If the associated entity manager is closed.
     */
    <T> T find(Class<T> cls, Object primaryKey, Number revision) throws
            IllegalArgumentException, NotAuditedException, IllegalStateException;

    /**
     * Get a list of revision numbers, at which an entity was modified.
     * @param cls Class of the entity.
     * @param primaryKey Primary key of the entity.
     * @return A list of revision numbers, at which the entity was modified, sorted in ascending order (so older
     * revisions come first).
     * @throws NotAuditedException When entities of the given class are not audited.
     * @throws IllegalArgumentException If cls or primaryKey is null.
     * @throws IllegalStateException If the associated entity manager is closed.
     */
    List<Number> getRevisions(Class<?> cls, Object primaryKey)
            throws IllegalArgumentException, NotAuditedException, IllegalStateException;

    /**
     * Get the date, at which a revision was created.
     * @param revision Number of the revision for which to get the date.
     * @return Date of commiting the given revision.
     * @throws IllegalArgumentException If revision is less or equal to 0.
     * @throws RevisionDoesNotExistException If the revision does not exist.
     * @throws IllegalStateException If the associated entity manager is closed.
     */
    Date getRevisionDate(Number revision) throws IllegalArgumentException, RevisionDoesNotExistException,
            IllegalStateException;

    /**
     * Gets the revision number, that corresponds to the given date. More precisely, returns
     * the number of the highest revision, which was created on or before the given date. So:
     * <code>getRevisionDate(getRevisionNumberForDate(date)) <= date</code> and
     * <code>getRevisionDate(getRevisionNumberForDate(date)+1) > date</code>.
     * @param date Date for which to get the revision.
     * @return Revision number corresponding to the given date.
     * @throws IllegalStateException If the associated entity manager is closed.
     * @throws RevisionDoesNotExistException If the given date is before the first revision.
     * @throws IllegalArgumentException If <code>date</code> is <code>null</code>.
     */
    Number getRevisionNumberForDate(Date date) throws IllegalStateException, RevisionDoesNotExistException,
            IllegalArgumentException;

    /**
     * A helper method; should be used only if a custom revision entity is used. See also {@link RevisionEntity}.
     * @param revisionEntityClass Class of the revision entity. Should be annotated with {@link RevisionEntity}.
     * @param revision Number of the revision for which to get the data.
     * @return Entity containing data for the given revision.
     * @throws IllegalArgumentException If revision is less or equal to 0 or if the class of the revision entity
     * is invalid.
     * @throws RevisionDoesNotExistException If the revision does not exist.
     * @throws IllegalStateException If the associated entity manager is closed.
     */
    <T> T findRevision(Class<T> revisionEntityClass, Number revision) throws IllegalArgumentException,
            RevisionDoesNotExistException, IllegalStateException;

    /**
     *
     * @return A query creator, associated with this AuditReader instance, with which queries can be
     * created and later executed. Shouldn't be used after the associated Session or EntityManager
     * is closed.
     */
    AuditQueryCreator createQuery();
}
