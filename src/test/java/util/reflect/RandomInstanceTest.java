package util.reflect;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.Test;

import lombok.RequiredArgsConstructor;
import util.reflect.ClassUtil.TypeReference;
import util.reflect.ValueGenerator.DefaultValueGenerator;

public class RandomInstanceTest {

	@RequiredArgsConstructor
	private static class ElementClass {
		enum MyEnum { A, B, C }
		enum MyOtherEnum { D, E, F }

		final int intField;
		final Double[] doubleArray;
		final String stringField;
		final Date dateField;
		final Instant instantField;
		final ZonedDateTime zonedDateTimeField;
		final MyEnum myEnum;
		final Enum<MyOtherEnum> myOtherEnum;
	}

	private static class TestBaseClass<T> {
		List<T> theList;
	}

	private static class TestClass<K, V> extends TestBaseClass<V[]> {
		Map<List<? extends List<K>>, V> theMap;
	}

	@Test
	public void seededRandomInstanceTest() throws ReflectiveOperationException {
		ClassContext<ElementClass> classContext = ClassUtil.analyze(ElementClass.class);
		ElementClass instance = classContext.randomInstance(new DefaultValueGenerator(new Random(0)));

		assertEquals(-1155484576, instance.intField);
		assertArrayEquals(new Double[] { 0.6063452159973596, 0.3090505681997092, 0.11700660880722513, 0.7815346320453048, 0.2527761665759859}, instance.doubleArray);
		assertEquals("a8b6fded-32df-30ca-bb2c-45a4b5944569", instance.stringField);
	}

	@Test
	public void pathBasedCollectionSizeRandomInstanceTest() throws ReflectiveOperationException {
		ClassContext<TestClass<String, Integer>> classContext = ClassUtil.analyze(new TypeReference<>() {});
		TestClass<String, Integer> instance = classContext.randomInstance(new DefaultValueGenerator() {
			@Override
			public int getCollectionSize(ContextualType<?> type, String path) {
				return path.equals("theMap") ? 0 : path.equals("theList") ? 2 : path.matches("theList\\[\\d+\\]") ? 1 : 3;
			}
		});

		assertEquals(0, instance.theMap.size());
		assertEquals(2, instance.theList.size());
		assertEquals(1, instance.theList.get(0).length);
		assertEquals(1, instance.theList.get(1).length);
		assertEquals(Integer.class, instance.theList.get(0)[0].getClass());
		assertEquals(Integer.class, instance.theList.get(1)[0].getClass());
	}

	private ElementClass assertElementClass(ElementClass element) {
		assertEquals(ElementClass.class, element.getClass());
		assertTrue(element.intField > 0);
		assertTrue(element.doubleArray[0] > 0);
		assertTrue(element.doubleArray[1] > 0);
		assertTrue(element.stringField.length() > 0);
		assertEquals(Date.class, element.dateField.getClass());
		assertEquals(Instant.class, element.instantField.getClass());
		assertEquals(ZonedDateTime.class, element.zonedDateTimeField.getClass());
		assertEquals(ElementClass.MyEnum.class, element.myEnum.getClass());
		assertEquals(ElementClass.MyOtherEnum.class, element.myOtherEnum.getClass());
		return element;
	}

	@Test
	public void basicRandomInstanceTest() throws ReflectiveOperationException {
		ValueGenerator theValueGenerator = new DefaultValueGenerator() {
			@Override
			public Object generate(ContextualType<?> type, String path, CurrentInstanceContext currentInstanceContext) throws ReflectiveOperationException {
				Class<?> clazz = type.getActualClass();
				if (clazz == int.class) return 1 + random.nextInt(100);
				if (clazz == Double.class) return Double.valueOf((1 + random.nextInt(10000)) / 100.);
				return super.generate(type, path, currentInstanceContext);
			}
		};

		ClassContext<TestClass<String, ElementClass>> classContext = ClassUtil.analyze(new TypeReference<>() {});
		TestClass<String, ElementClass> instance1 = classContext.randomInstance(theValueGenerator);

		assertEquals(LinkedHashMap.class, instance1.theMap.getClass());
		assertEquals(ArrayList.class, instance1.theMap.keySet().iterator().next().getClass());
		assertEquals(ArrayList.class, instance1.theMap.keySet().iterator().next().get(0).getClass());
		assertEquals(ArrayList.class, instance1.theList.getClass());

		assertTrue(instance1.theMap.keySet().iterator().next().get(0).get(0).length() > 0);
		assertElementClass(instance1.theMap.values().iterator().next());
		assertElementClass(instance1.theList.get(0)[0]);
		assertElementClass(instance1.theList.get(0)[1]);
		assertElementClass(instance1.theList.get(1)[0]);
		assertElementClass(instance1.theList.get(0)[1]);

		// assert different instances have different field values
		TestClass<String, ElementClass> instance2 = classContext.randomInstance(theValueGenerator);
		assertNotEquals(assertElementClass(instance1.theMap.values().iterator().next()).stringField, assertElementClass(instance2.theMap.values().iterator().next()).stringField);
	}


	private static class Egg {
		String name;
		Chicken chicken;
	}

	private static class Chicken {
		String name;
		Egg egg;
	}

	@Test
	public void recursiveRandomInstance_defaultValueGenerator_recursedFieldIsNull() throws ReflectiveOperationException {
		ClassContext<Chicken> classContext = ClassUtil.analyze(Chicken.class);
		Chicken instance = classContext.randomInstance();

		assertTrue(instance.name.length() > 0);
		assertNotNull(instance.egg);
		assertTrue(instance.name.length() > 0);
		assertNull(instance.egg.chicken);
	}

	@Test
	public void recursiveRandomInstance_customValueGenerator_recursedFieldIsSetToExistingInstance() throws ReflectiveOperationException {
		ClassContext<Chicken> classContext = ClassUtil.analyze(Chicken.class);
		Chicken instance = classContext.randomInstance(new DefaultValueGenerator() {
			@Override
			public Object onRecursion(ContextualType<?> type, String path, List<Object> recursed, CurrentInstanceContext currentInstanceContext) throws ReflectiveOperationException {
				return recursed.size() >= 2 ? recursed.get(0) : currentInstanceContext.randomInstance();
			}
		});

		assertTrue(instance.name.length() > 0);
		assertTrue(instance.egg.name.length() > 0);
		assertTrue(instance.egg.chicken.name.length() > 0);
		assertTrue(instance != instance.egg.chicken);
		assertTrue(instance == instance.egg.chicken.egg.chicken);
	}


	private static class OuterClass<S> {
		S outerField;
		private class MyClass<T> {
			Set<S> mySField;
			List<T> myTField;
			OuterClass<S> getOuterInstance() {
				return OuterClass.this;
			}
		}
	}

	@Test
	public void outerInstanceTest() throws ReflectiveOperationException {
		ClassContext<OuterClass<String>.MyClass<Integer>> classContext = ClassUtil.analyze(new TypeReference<>() {});
		OuterClass<String>.MyClass<Integer> instance = classContext.randomInstance();

		assertEquals(Integer.class, instance.myTField.get(0).getClass());
		assertTrue(instance.mySField.iterator().next().length() > 0);
		assertTrue(instance.getOuterInstance().outerField.length() > 0);
	}


	private static class Infinite<T> {
		Infinite<List<T>> infinite;
		T field;
	}

	@Test
	public void infinitelyCascadingTypeRandomInstanceWithCustomValueGeneratorTest() throws ReflectiveOperationException {
		ClassContext<Infinite<String>> classContext = ClassUtil.analyze(new TypeReference<>() {});
		Infinite<String> instance = classContext.randomInstance(new DefaultValueGenerator() {
			@Override
			public Object generate(ContextualType<?> type, String path, CurrentInstanceContext currentInstanceContext) throws ReflectiveOperationException {
				if (path.split("\\.").length > 3) return null;
				return super.generate(type, path, currentInstanceContext);
			}
		});

		assertNotNull(instance.infinite);
		assertNotNull(instance.infinite.infinite);

		assertNotNull(instance.infinite.infinite.infinite);
		assertTrue(instance.infinite.infinite.field.get(0).get(0).length() > 0);

		assertNull(instance.infinite.infinite.infinite.infinite);
		assertNull(instance.infinite.infinite.infinite.field);
	}
}
