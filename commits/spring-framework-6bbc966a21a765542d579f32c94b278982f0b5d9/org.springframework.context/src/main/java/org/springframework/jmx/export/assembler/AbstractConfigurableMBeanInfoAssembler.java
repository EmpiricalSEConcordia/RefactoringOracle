/*
 * Copyright 2002-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.jmx.export.assembler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.management.modelmbean.ModelMBeanNotificationInfo;

import org.springframework.jmx.export.metadata.JmxMetadataUtils;
import org.springframework.jmx.export.metadata.ManagedNotification;
import org.springframework.util.StringUtils;

/**
 * Base class for MBeanInfoAssemblers that support configurable
 * JMX notification behavior.
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @since 2.0
 */
public abstract class AbstractConfigurableMBeanInfoAssembler extends AbstractReflectiveMBeanInfoAssembler {

	private ModelMBeanNotificationInfo[] notificationInfos;

	private final Map notificationInfoMappings = new HashMap();


	public void setNotificationInfos(ManagedNotification[] notificationInfos) {
		ModelMBeanNotificationInfo[] infos = new ModelMBeanNotificationInfo[notificationInfos.length];
		for (int i = 0; i < notificationInfos.length; i++) {
			ManagedNotification notificationInfo = notificationInfos[i];
			infos[i] = JmxMetadataUtils.convertToModelMBeanNotificationInfo(notificationInfo);
		}
		this.notificationInfos = infos;
	}

	public void setNotificationInfoMappings(Map notificationInfoMappings) {
		Iterator entries = notificationInfoMappings.entrySet().iterator();
		while (entries.hasNext()) {
			Map.Entry entry = (Map.Entry) entries.next();
			if (!(entry.getKey() instanceof String)) {
				throw new IllegalArgumentException("Property [notificationInfoMappings] only accepts Strings for Map keys");
			}
			this.notificationInfoMappings.put(entry.getKey(), extractNotificationMetadata(entry.getValue()));
		}
	}


	@Override
	protected ModelMBeanNotificationInfo[] getNotificationInfo(Object managedBean, String beanKey) {
		ModelMBeanNotificationInfo[] result = null;

		if (StringUtils.hasText(beanKey)) {
			result = (ModelMBeanNotificationInfo[]) this.notificationInfoMappings.get(beanKey);
		}

		if (result == null) {
			result = this.notificationInfos;
		}

		return (result == null) ? new ModelMBeanNotificationInfo[0] : result;
	}

	private ModelMBeanNotificationInfo[] extractNotificationMetadata(Object mapValue) {
		if (mapValue instanceof ManagedNotification) {
			ManagedNotification mn = (ManagedNotification) mapValue;
			return new ModelMBeanNotificationInfo[] {JmxMetadataUtils.convertToModelMBeanNotificationInfo(mn)};
		}
		else if (mapValue instanceof Collection) {
			Collection col = (Collection) mapValue;
			List result = new ArrayList();
			for (Iterator iterator = col.iterator(); iterator.hasNext();) {
				Object colValue = iterator.next();
				if (!(colValue instanceof ManagedNotification)) {
					throw new IllegalArgumentException(
							"Property 'notificationInfoMappings' only accepts ManagedNotifications for Map values");
				}
				ManagedNotification mn = (ManagedNotification) colValue;
				result.add(JmxMetadataUtils.convertToModelMBeanNotificationInfo(mn));
			}
			return (ModelMBeanNotificationInfo[]) result.toArray(new ModelMBeanNotificationInfo[result.size()]);
		}
		else {
			throw new IllegalArgumentException(
					"Property 'notificationInfoMappings' only accepts ManagedNotifications for Map values");
		}
	}

}
