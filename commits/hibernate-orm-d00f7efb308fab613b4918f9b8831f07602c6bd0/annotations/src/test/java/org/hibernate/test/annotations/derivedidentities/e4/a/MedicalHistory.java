package org.hibernate.test.annotations.derivedidentities.e4.a;

import java.io.Serializable;
import java.util.Date;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.MapsId;
import javax.persistence.OneToOne;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;


/**
 * @author Emmanuel Bernard
 */
@Entity
public class MedicalHistory implements Serializable {

	@Temporal(TemporalType.DATE)
	Date lastupdate;

	@Id
	@JoinColumn(name = "FK")
	@OneToOne
	Person patient;
}