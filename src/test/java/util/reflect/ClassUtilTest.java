package util.reflect;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.OffsetDateTime;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import util.reflect.ClassUtil.TypeReference;
import util.reflect.ValueGenerator.DefaultValueGenerator;

public class ClassUtilTest {

	@Test
	public void analyzePrimitiveType_returnInternedInstance() {
		assertTrue(ClassUtil.analyze(int.class) == ClassUtil.analyze(int.class));
		assertTrue(ClassUtil.analyze(Integer.class) == ClassUtil.analyze(Integer.class));
		assertTrue(ClassUtil.analyze(String.class) == ClassUtil.analyze(String.class));

		assertNotEquals(ClassUtil.analyze(int.class), ClassUtil.analyze(Integer.class));
		assertNotEquals(ClassUtil.analyze(String.class), ClassUtil.analyze(Integer.class));
	}

	@Test
	public void analyzeSimpleArrayType_returnArrayContext() {
		ArrayContext<String[]> arrayContext = ClassUtil.analyze(String[].class);
		assertEquals(String[].class, arrayContext.getActualClass());
		assertEquals(String[].class, arrayContext.getResolvedType());
		assertEquals(String.class, arrayContext.getComponentType().getActualClass());
	}

	@Test
	public void analyzeGenericArrayType_returnArrayContext() {
		ArrayContext<List<String>[]> arrayContext = ClassUtil.analyze(new TypeReference<>() {});

		assertEquals(List[].class, arrayContext.getActualClass());
		GenericArrayType theType = (GenericArrayType) arrayContext.getResolvedType();
		assertParameterizedType((ParameterizedType) theType.getGenericComponentType(), List.class, String.class);

		assertEquals(List.class, arrayContext.getComponentType().getActualClass());
		assertParameterizedType((ParameterizedType) arrayContext.getComponentType().getResolvedType(), List.class, String.class);
	}

	@Test
	public void analyzeParameterizedType_returnClassContext() throws NoSuchFieldException {
		ClassContext<ArrayList<String>> classContext = ClassUtil.analyze(new TypeReference<>() {});

		assertEquals(ArrayList.class, classContext.getActualClass());
		assertEquals(Map.of(ArrayList.class.getTypeParameters()[0], String.class), classContext.getContext());
		assertParameterizedType((ParameterizedType) classContext.getResolvedType(), ArrayList.class, String.class);

		assertNull(classContext.getOuterClass());

		assertEquals(AbstractList.class, classContext.getSuperclass().getActualClass());
		assertEquals(Map.of(AbstractList.class.getTypeParameters()[0], String.class), classContext.getSuperclass().getContext());

		assertEquals(Map.of(List.class.getTypeParameters()[0], String.class), classContext.getInterface(List.class).getContext());
		assertEquals(Map.of(Collection.class.getTypeParameters()[0], String.class), classContext.getInterface(Collection.class).getContext());

		assertEquals(ArrayList.class.getDeclaredField("size"), classContext.getField("size"));
		assertEquals(int.class, classContext.getFieldType("size").getActualClass());
	}

	@Test
	public void internParameterizedType_returnInternedInstance() {
		ClassContext<ArrayList<Integer>> classContext1 = ClassUtil.analyze(new TypeReference<>() {});
		ClassContext<ArrayList<Integer>> classContext2 = ClassUtil.analyze(new TypeReference<>() {});
		assertEquals(classContext1, classContext2);
		assertTrue(classContext1 != classContext2);

		assertTrue(classContext2 == classContext2.intern());
		assertTrue(classContext2 == classContext1.intern());

		assertTrue(classContext2 == ClassUtil.analyze(new TypeReference<ArrayList<Integer>>() {}));
	}

	private static class MyTypeReference extends TypeReference<ArrayList<String>> {
	}

	@Test(expected = IllegalStateException.class)
	public void subclassOfsubclassOfTypeReference_throwsIllegalStateException() {
		new MyTypeReference() {};
	}

	private void assertParameterizedType(ParameterizedType actual, Type expectedRaw, Type ... expectedTypes) {
		assertEquals(expectedRaw, actual.getRawType());
		assertArrayEquals(expectedTypes, actual.getActualTypeArguments());
	}


	@EqualsAndHashCode
	@ToString
	private static class MyItem {
		String uuid;
	}

	@EqualsAndHashCode
	@ToString
	private static class MyClass {
		enum MyEnum { A, B, C }

		String aString;
		int anInt;
		Integer anInteger;
		Date aDate;
		OffsetDateTime anOffsetDateTime;
		Class<?> aClass;
		MyEnum anEnum;
		Enum<MyEnum> myEnum;

		float[] aPrimitiveFloatArray;
		Double[] aDoubleArray;

		List<MyItem> aList;
		Map<String, MyItem> aMap;

		MyClass myClassRef;
	}

	@EqualsAndHashCode
	@ToString
	private static class Outer {

		String outer;

		@EqualsAndHashCode
		@ToString
		private class Middle {

			String middle;

			@EqualsAndHashCode(callSuper = true)
			@ToString(callSuper = true)
			private class Inner extends MyClass {
				String inner;
				Middle getMiddle() {
					return Middle.this;
				}
				Outer getOuter() {
					return Outer.this;
				}
			}
		}
	}

	@Test
	public void getOuterInstanceTest() throws ReflectiveOperationException {
		Outer.Middle.Inner inner = new Outer().new Middle().new Inner();
		assertTrue(ClassUtil.getOuterInstance(inner) == inner.getMiddle());
		assertTrue(ClassUtil.getOuterInstance(inner.getMiddle()) == inner.getOuter());
		assertNull(ClassUtil.getOuterInstance(inner.getOuter()));
	}

	private void assertClone(Object original, Object clone) {
		assertTrue(original != clone);
		assertEquals(original, clone);
	}

	@Test
	public void shallowCloneTest() throws ReflectiveOperationException {
		Outer.Middle.Inner instance = ClassUtil.analyze(Outer.Middle.Inner.class).randomInstance(new DefaultValueGenerator() {
			@Override
			public Object generate(ContextualType<?> type, String path, CurrentInstanceContext currentInstanceContext) throws ReflectiveOperationException {
				Class<?> clazz = type.getActualClass();
				if (clazz == int.class || clazz == Integer.class) return 1 + random.nextInt(100);
				if (clazz == float.class) return Float.valueOf((1 + random.nextInt(10000)) / 100.f);
				if (clazz == double.class) return Double.valueOf((1 + random.nextInt(10000)) / 100.);
				return super.generate(type, path, currentInstanceContext);
			}
		});
		Outer.Middle.Inner clone = ClassUtil.shallowClone(instance);

		assertNotNull(instance.inner);
		assertNotNull(instance.aString);
		assertTrue(instance.anInt > 0);
		assertTrue(instance.anInteger > 0);
		assertNotNull(instance.aDate);
		assertNotNull(instance.anOffsetDateTime);
		assertNotNull(instance.aClass);
		assertNotNull(instance.anEnum);
		assertNotNull(instance.myEnum);
		assertTrue(instance.aPrimitiveFloatArray[0] > 0);
		assertTrue(instance.aDoubleArray[0] > 0);

		assertClone(instance, clone);
		assertTrue(instance.aList == clone.aList);
		assertTrue(instance.aMap == clone.aMap);

		assertTrue(instance.aList.get(0) == clone.aList.get(0));
		assertTrue(instance.aMap.values().iterator().next() == clone.aMap.values().iterator().next());

		assertNotNull(instance.getMiddle().middle);
		assertNotNull(instance.getOuter().outer);
		assertTrue(instance.getMiddle() == clone.getMiddle());
		assertTrue(instance.getOuter() == clone.getOuter());
	}

	@Test
	public void deepCloneTest() throws ReflectiveOperationException {
		Outer.Middle.Inner instance = ClassUtil.analyze(Outer.Middle.Inner.class).randomInstance(new DefaultValueGenerator() {
			@Override
			public Object generate(ContextualType<?> type, String path, CurrentInstanceContext currentInstanceContext) throws ReflectiveOperationException {
				Class<?> clazz = type.getActualClass();
				if (clazz == int.class || clazz == Integer.class) return 1 + random.nextInt(100);
				if (clazz == float.class) return Float.valueOf((1 + random.nextInt(10000)) / 100.f);
				if (clazz == double.class) return Double.valueOf((1 + random.nextInt(10000)) / 100.);
				return super.generate(type, path, currentInstanceContext);
			}
		});
		Outer.Middle.Inner clone = ClassUtil.deepClone(instance);

		assertNotNull(instance.inner);
		assertNotNull(instance.aString);
		assertTrue(instance.anInt > 0);
		assertTrue(instance.anInteger > 0);
		assertNotNull(instance.aDate);
		assertNotNull(instance.anOffsetDateTime);
		assertNotNull(instance.aClass);
		assertNotNull(instance.anEnum);
		assertNotNull(instance.myEnum);
		assertTrue(instance.aPrimitiveFloatArray[0] > 0);
		assertTrue(instance.aDoubleArray[0] > 0);

		assertClone(instance, clone);
		assertTrue(instance.aList != clone.aList);
		assertTrue(instance.aMap != clone.aMap);

		assertClone(instance.aList.get(0), clone.aList.get(0));
		assertClone(instance.aMap.values().iterator().next(), clone.aMap.values().iterator().next());

		assertNotNull(instance.getMiddle().middle);
		assertNotNull(instance.getOuter().outer);
		assertClone(instance.getMiddle(), clone.getMiddle());
		assertClone(instance.getOuter(), clone.getOuter());
	}

	@Test
	public void deepCloneSelfReferenceTest() throws ReflectiveOperationException {
		Outer.Middle.Inner instance = ClassUtil.analyze(Outer.Middle.Inner.class).randomInstance();
		instance.myClassRef = instance;
		Outer.Middle.Inner clone = ClassUtil.deepClone(instance);

		assertTrue(instance != clone);
		assertTrue(clone.myClassRef == clone);
	}
}
