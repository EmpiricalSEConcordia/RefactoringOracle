package org.springframework.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;

/**
 * Unit tests for {@link org.springframework.binding.collection.SharedMapDecorator}.
 */
public class SharedMapDecoratorTests extends TestCase {

	private SharedMapDecorator map = new SharedMapDecorator(new HashMap());

	public void testGetPutRemove() {
		assertTrue(map.size() == 0);
		assertTrue(map.isEmpty());
		assertNull(map.get("foo"));
		assertFalse(map.containsKey("foo"));
		map.put("foo", "bar");
		assertTrue(map.size() == 1);
		assertFalse(map.isEmpty());
		assertNotNull(map.get("foo"));
		assertTrue(map.containsKey("foo"));
		assertTrue(map.containsValue("bar"));
		assertEquals("bar", map.get("foo"));
		map.remove("foo");
		assertTrue(map.size() == 0);
		assertNull(map.get("foo"));
	}

	public void testPutAll() {
		Map all = new HashMap();
		all.put("foo", "bar");
		all.put("bar", "baz");
		map.putAll(all);
		assertTrue(map.size() == 2);
	}

	public void testEntrySet() {
		map.put("foo", "bar");
		map.put("bar", "baz");
		Set entrySet = map.entrySet();
		assertTrue(entrySet.size() == 2);
	}

	public void testKeySet() {
		map.put("foo", "bar");
		map.put("bar", "baz");
		Set keySet = map.keySet();
		assertTrue(keySet.size() == 2);
	}

	public void testValues() {
		map.put("foo", "bar");
		map.put("bar", "baz");
		Collection values = map.values();
		assertTrue(values.size() == 2);
	}
}
