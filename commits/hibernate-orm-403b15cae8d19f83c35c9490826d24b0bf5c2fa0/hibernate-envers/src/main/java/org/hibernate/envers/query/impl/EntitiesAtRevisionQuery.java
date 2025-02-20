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
package org.hibernate.envers.query.impl;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.hibernate.Query;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.configuration.AuditConfiguration;
import org.hibernate.envers.configuration.AuditEntitiesConfiguration;
import org.hibernate.envers.entities.mapper.relation.MiddleIdData;
import org.hibernate.envers.query.criteria.AuditCriterion;
import org.hibernate.envers.reader.AuditReaderImplementor;

/**
 * @author Adam Warski (adam at warski dot org)
 * @author Hern�n Chanfreau
 */
public class EntitiesAtRevisionQuery extends AbstractAuditQuery {
    private final Number revision;

    public EntitiesAtRevisionQuery(AuditConfiguration verCfg,
                                   AuditReaderImplementor versionsReader, Class<?> cls,
                                   Number revision) {
        super(verCfg, versionsReader, cls);
        this.revision = revision;
    }
    
	public EntitiesAtRevisionQuery(AuditConfiguration verCfg,
                                   AuditReaderImplementor versionsReader, Class<?> cls, String entityName,
                                   Number revision) {
		super(verCfg, versionsReader, cls, entityName);
		this.revision = revision;
	}    

    @SuppressWarnings({"unchecked"})
    public List list() {
        /*
         * The query that we need to create:
         *   SELECT new list(e) FROM versionsReferencedEntity e
         *   WHERE
         * (all specified conditions, transformed, on the "e" entity) AND
         * (selecting e entities at revision :revision)
         *   --> for DefaultAuditStrategy:
         *     e.revision = (SELECT max(e2.revision) FROM versionsReferencedEntity e2
         *       WHERE e2.revision <= :revision AND e2.id = e.id) 
         *     
         *   --> for ValidityAuditStrategy:
         *     e.revision <= :revision and (e.endRevision > :revision or e.endRevision is null)
         *     
         *     AND
         * (only non-deleted entities)
         *     e.revision_type != DEL
         */
        AuditEntitiesConfiguration verEntCfg = verCfg.getAuditEntCfg();
        String revisionPropertyPath = verEntCfg.getRevisionNumberPath();
        String originalIdPropertyName = verEntCfg.getOriginalIdPropName();

        MiddleIdData referencedIdData = new MiddleIdData(verEntCfg, verCfg.getEntCfg().get(entityName).getIdMappingData(), 
        		null, entityName, verCfg.getEntCfg().isVersioned(entityName));

        // (selecting e entities at revision :revision)
        // --> based on auditStrategy (see above)
        verCfg.getAuditStrategy().addEntityAtRevisionRestriction(verCfg.getGlobalCfg(), qb, revisionPropertyPath, 
        		verEntCfg.getRevisionEndFieldName(), true, referencedIdData, 
				revisionPropertyPath, originalIdPropertyName, "e", "e2");

        // e.revision_type != DEL
        qb.getRootParameters().addWhereWithParam(verEntCfg.getRevisionTypePropName(), "<>", RevisionType.DEL);

        // all specified conditions
        for (AuditCriterion criterion : criterions) {
            criterion.addToQuery(verCfg, entityName, qb, qb.getRootParameters());
        }
        
        Query query = buildQuery();
        // add named parameter (only used for ValidAuditTimeStrategy) 
        List<String> params = Arrays.asList(query.getNamedParameters());
        if (params.contains("revision")) {
            query.setParameter("revision", revision);
        }
        List queryResult = query.list();

        if (hasProjection) {
            return queryResult;
        } else {
            List result = new ArrayList();
            entityInstantiator.addInstancesFromVersionsEntities(entityName, result, queryResult, revision);

            return result;
        }
    }
}
