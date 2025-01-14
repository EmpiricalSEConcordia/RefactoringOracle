package org.hibernate.envers.test.integration.reventity.trackmodifiedentities;

import org.hibernate.ejb.Ejb3Configuration;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.EntityTrackingRevisionListener;
import org.hibernate.envers.exception.AuditException;
import org.hibernate.envers.test.AbstractEntityTest;
import org.hibernate.envers.test.Priority;
import org.hibernate.envers.test.entities.StrIntTestEntity;
import org.hibernate.envers.test.entities.StrTestEntity;
import org.hibernate.envers.test.entities.reventity.trackmodifiedentities.CustomTrackingRevisionEntity;
import org.hibernate.envers.test.entities.reventity.trackmodifiedentities.CustomTrackingRevisionListener;
import org.hibernate.envers.test.entities.reventity.trackmodifiedentities.ModifiedEntityTypeEntity;
import org.hibernate.envers.test.tools.TestTools;
import org.junit.Test;

import javax.persistence.EntityManager;

/**
 * Tests proper behavior of entity listener that implements {@link EntityTrackingRevisionListener}
 * interface. {@link CustomTrackingRevisionListener} shall be notified whenever an entity instance has been
 * added, modified or removed, so that changed entity type can be persisted.
 * @author Lukasz Antoniak (lukasz dot antoniak at gmail dot com)
 */
public class CustomTrackingEntitiesTest extends AbstractEntityTest {
    private Integer steId = null;
    private Integer siteId = null;
    
    @Override
    public void configure(Ejb3Configuration cfg) {
        cfg.addAnnotatedClass(ModifiedEntityTypeEntity.class);
        cfg.addAnnotatedClass(CustomTrackingRevisionEntity.class);
        cfg.addAnnotatedClass(StrTestEntity.class);
        cfg.addAnnotatedClass(StrIntTestEntity.class);
    }

    @Test
    @Priority(10)
    public void initData() {
        EntityManager em = getEntityManager();

        // Revision 1 - Adding two entities
        em.getTransaction().begin();
        StrTestEntity ste = new StrTestEntity("x");
        StrIntTestEntity site = new StrIntTestEntity("y", 1);
        em.persist(ste);
        em.persist(site);
        steId = ste.getId();
        siteId = site.getId();
        em.getTransaction().commit();

        // Revision 2 - Modifying one entity
        em.getTransaction().begin();
        site = em.find(StrIntTestEntity.class, siteId);
        site.setNumber(2);
        em.getTransaction().commit();

        // Revision 3 - Deleting both entities
        em.getTransaction().begin();
        ste = em.find(StrTestEntity.class, steId);
        site = em.find(StrIntTestEntity.class, siteId);
        em.remove(ste);
        em.remove(site);
        em.getTransaction().commit();
    }

    @Test
    public void testTrackAddedEntities() {
        ModifiedEntityTypeEntity steDescriptor = new ModifiedEntityTypeEntity(StrTestEntity.class.getName());
        ModifiedEntityTypeEntity siteDescriptor = new ModifiedEntityTypeEntity(StrIntTestEntity.class.getName());

        AuditReader vr = getAuditReader();
        CustomTrackingRevisionEntity ctre = vr.findRevision(CustomTrackingRevisionEntity.class, 1);

        assert ctre.getModifiedEntityTypes() != null;
        assert ctre.getModifiedEntityTypes().size() == 2;
        assert TestTools.makeSet(steDescriptor, siteDescriptor).equals(ctre.getModifiedEntityTypes());
    }

    @Test
    public void testTrackModifiedEntities() {
        ModifiedEntityTypeEntity siteDescriptor = new ModifiedEntityTypeEntity(StrIntTestEntity.class.getName());

        AuditReader vr = getAuditReader();
        CustomTrackingRevisionEntity ctre = vr.findRevision(CustomTrackingRevisionEntity.class, 2);

        assert ctre.getModifiedEntityTypes() != null;
        assert ctre.getModifiedEntityTypes().size() == 1;
        assert TestTools.makeSet(siteDescriptor).equals(ctre.getModifiedEntityTypes());
    }

    @Test
    public void testTrackDeletedEntities() {
        ModifiedEntityTypeEntity steDescriptor = new ModifiedEntityTypeEntity(StrTestEntity.class.getName());
        ModifiedEntityTypeEntity siteDescriptor = new ModifiedEntityTypeEntity(StrIntTestEntity.class.getName());

        AuditReader vr = getAuditReader();
        CustomTrackingRevisionEntity ctre = vr.findRevision(CustomTrackingRevisionEntity.class, 3);

        assert ctre.getModifiedEntityTypes() != null;
        assert ctre.getModifiedEntityTypes().size() == 2;
        assert TestTools.makeSet(steDescriptor, siteDescriptor).equals(ctre.getModifiedEntityTypes());
    }

    @Test(expected = AuditException.class)
    public void testFindEntitiesChangedInRevisionException() {
        getAuditReader().findEntitiesChangedInRevision(1);
    }
}
