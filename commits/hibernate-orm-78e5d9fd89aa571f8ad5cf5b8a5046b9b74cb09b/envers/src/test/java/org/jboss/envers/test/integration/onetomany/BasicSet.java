package org.jboss.envers.test.integration.onetomany;

import org.jboss.envers.test.AbstractEntityTest;
import org.jboss.envers.test.entities.onetomany.SetRefEdEntity;
import org.jboss.envers.test.entities.onetomany.SetRefIngEntity;
import org.jboss.envers.test.tools.TestTools;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.hibernate.ejb.Ejb3Configuration;

import javax.persistence.EntityManager;
import java.util.Arrays;
import java.util.Collections;

/**
 * @author Adam Warski (adam at warski dot org)
 */
public class BasicSet extends AbstractEntityTest {
    private Integer ed1_id;
    private Integer ed2_id;

    private Integer ing1_id;
    private Integer ing2_id;

    public void configure(Ejb3Configuration cfg) {
        cfg.addAnnotatedClass(SetRefEdEntity.class);
        cfg.addAnnotatedClass(SetRefIngEntity.class);
    }

    @BeforeClass(dependsOnMethods = "init")
    public void initData() {
        EntityManager em = getEntityManager();

        SetRefEdEntity ed1 = new SetRefEdEntity(1, "data_ed_1");
        SetRefEdEntity ed2 = new SetRefEdEntity(2, "data_ed_2");

        SetRefIngEntity ing1 = new SetRefIngEntity(3, "data_ing_1");
        SetRefIngEntity ing2 = new SetRefIngEntity(4, "data_ing_2");

        // Revision 1
        em.getTransaction().begin();

        em.persist(ed1);
        em.persist(ed2);

        em.getTransaction().commit();

        // Revision 2

        em.getTransaction().begin();

        ed1 = em.find(SetRefEdEntity.class, ed1.getId());

        ing1.setReference(ed1);
        ing2.setReference(ed1);

        em.persist(ing1);
        em.persist(ing2);

        em.getTransaction().commit();

        // Revision 3
        em.getTransaction().begin();

        ing1 = em.find(SetRefIngEntity.class, ing1.getId());
        ed2 = em.find(SetRefEdEntity.class, ed2.getId());

        ing1.setReference(ed2);

        em.getTransaction().commit();

        // Revision 4
        em.getTransaction().begin();

        ing2 = em.find(SetRefIngEntity.class, ing2.getId());
        ed2 = em.find(SetRefEdEntity.class, ed2.getId());

        ing2.setReference(ed2);

        em.getTransaction().commit();

        //

        ed1_id = ed1.getId();
        ed2_id = ed2.getId();

        ing1_id = ing1.getId();
        ing2_id = ing2.getId();
    }

    @Test
    public void testRevisionsCounts() {
        assert Arrays.asList(1, 2, 3, 4).equals(getVersionsReader().getRevisions(SetRefEdEntity.class, ed1_id));
        assert Arrays.asList(1, 3, 4).equals(getVersionsReader().getRevisions(SetRefEdEntity.class, ed2_id));

        assert Arrays.asList(2, 3).equals(getVersionsReader().getRevisions(SetRefIngEntity.class, ing1_id));
        assert Arrays.asList(2, 4).equals(getVersionsReader().getRevisions(SetRefIngEntity.class, ing2_id));
    }

    @Test
    public void testHistoryOfEdId1() {
        SetRefIngEntity ing1 = getEntityManager().find(SetRefIngEntity.class, ing1_id);
        SetRefIngEntity ing2 = getEntityManager().find(SetRefIngEntity.class, ing2_id);

        SetRefEdEntity rev1 = getVersionsReader().find(SetRefEdEntity.class, ed1_id, 1);
        SetRefEdEntity rev2 = getVersionsReader().find(SetRefEdEntity.class, ed1_id, 2);
        SetRefEdEntity rev3 = getVersionsReader().find(SetRefEdEntity.class, ed1_id, 3);
        SetRefEdEntity rev4 = getVersionsReader().find(SetRefEdEntity.class, ed1_id, 4);

        assert rev1.getReffering().equals(Collections.EMPTY_SET);
        assert rev2.getReffering().equals(TestTools.makeSet(ing1, ing2));
        assert rev3.getReffering().equals(TestTools.makeSet(ing2));
        assert rev4.getReffering().equals(Collections.EMPTY_SET);
    }

    @Test
    public void testHistoryOfEdId2() {
        SetRefIngEntity ing1 = getEntityManager().find(SetRefIngEntity.class, ing1_id);
        SetRefIngEntity ing2 = getEntityManager().find(SetRefIngEntity.class, ing2_id);

        SetRefEdEntity rev1 = getVersionsReader().find(SetRefEdEntity.class, ed2_id, 1);
        SetRefEdEntity rev2 = getVersionsReader().find(SetRefEdEntity.class, ed2_id, 2);
        SetRefEdEntity rev3 = getVersionsReader().find(SetRefEdEntity.class, ed2_id, 3);
        SetRefEdEntity rev4 = getVersionsReader().find(SetRefEdEntity.class, ed2_id, 4);

        assert rev1.getReffering().equals(Collections.EMPTY_SET);
        assert rev2.getReffering().equals(Collections.EMPTY_SET);
        assert rev3.getReffering().equals(TestTools.makeSet(ing1));
        assert rev4.getReffering().equals(TestTools.makeSet(ing1, ing2));
    }

    @Test
    public void testHistoryOfEdIng1() {
        SetRefEdEntity ed1 = getEntityManager().find(SetRefEdEntity.class, ed1_id);
        SetRefEdEntity ed2 = getEntityManager().find(SetRefEdEntity.class, ed2_id);

        SetRefIngEntity rev1 = getVersionsReader().find(SetRefIngEntity.class, ing1_id, 1);
        SetRefIngEntity rev2 = getVersionsReader().find(SetRefIngEntity.class, ing1_id, 2);
        SetRefIngEntity rev3 = getVersionsReader().find(SetRefIngEntity.class, ing1_id, 3);
        SetRefIngEntity rev4 = getVersionsReader().find(SetRefIngEntity.class, ing1_id, 4);

        assert rev1 == null;
        assert rev2.getReference().equals(ed1);
        assert rev3.getReference().equals(ed2);
        assert rev4.getReference().equals(ed2);
    }

    @Test
    public void testHistoryOfEdIng2() {
        SetRefEdEntity ed1 = getEntityManager().find(SetRefEdEntity.class, ed1_id);
        SetRefEdEntity ed2 = getEntityManager().find(SetRefEdEntity.class, ed2_id);

        SetRefIngEntity rev1 = getVersionsReader().find(SetRefIngEntity.class, ing2_id, 1);
        SetRefIngEntity rev2 = getVersionsReader().find(SetRefIngEntity.class, ing2_id, 2);
        SetRefIngEntity rev3 = getVersionsReader().find(SetRefIngEntity.class, ing2_id, 3);
        SetRefIngEntity rev4 = getVersionsReader().find(SetRefIngEntity.class, ing2_id, 4);

        assert rev1 == null;
        assert rev2.getReference().equals(ed1);
        assert rev3.getReference().equals(ed1);
        assert rev4.getReference().equals(ed2);
    }
}
