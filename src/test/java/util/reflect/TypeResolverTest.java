package util.reflect;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import util.reflect.ClassUtil.TypeReference;

public class TypeResolverTest {

	private static abstract class MyAbstractMap<K, V> extends AbstractMap<K, V> {
	}

	private static class MyClass {
	}

	@Test
	public void resolveTest() {
		ParameterizedType type = (ParameterizedType) TypeResolver.resolve(
				MyAbstractMap.class.getGenericSuperclass(),
				Map.of(MyAbstractMap.class.getTypeParameters()[0], String.class,
						MyAbstractMap.class.getTypeParameters()[1], Integer.class));

		assertEquals(AbstractMap.class, type.getRawType());
		assertArrayEquals(new Type[] { String.class, Integer.class }, type.getActualTypeArguments());
		assertNull(type.getOwnerType());
	}

	@Test
	public void newResolvedClassType_returnsParameterizedType() {
		ParameterizedType type = (ParameterizedType) TypeResolver.newResolvedClassType(
				MyAbstractMap.class,
				Map.of(MyAbstractMap.class.getTypeParameters()[0], String.class,
						MyAbstractMap.class.getTypeParameters()[1], Integer.class));

		assertEquals(MyAbstractMap.class, type.getRawType());
		assertArrayEquals(new Type[] { String.class, Integer.class }, type.getActualTypeArguments());
		assertEquals(TypeResolverTest.class, type.getOwnerType());
	}

	@Test
	public void newResolvedClassType_returnsClass() {
		Class<?> clazz = (Class<?>) TypeResolver.newResolvedClassType(MyClass.class, Collections.emptyMap());
		assertEquals(MyClass.class, clazz);
		assertEquals(TypeResolverTest.class, clazz.getEnclosingClass());
	}

	@Test
	public void newResolvedArrayType_returnsGenericArrayType() {
		GenericArrayType type = (GenericArrayType) TypeResolver.newResolvedArrayType(new TypeReference<List<String>>() {}.getType());
		ParameterizedType componentType = (ParameterizedType) type.getGenericComponentType();
		assertEquals(List.class, componentType.getRawType());
		assertArrayEquals(new Type[] { String.class }, componentType.getActualTypeArguments());
		assertNull(componentType.getOwnerType());
	}

	@Test
	public void newResolvedArrayType_returnsClass() {
		Class<?> clazz = (Class<?>) TypeResolver.newResolvedArrayType(MyClass.class);
		assertEquals(MyClass[].class, clazz);
	}
}
