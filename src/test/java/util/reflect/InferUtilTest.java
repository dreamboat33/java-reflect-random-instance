package util.reflect;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import org.junit.Test;

import util.reflect.ClassUtil.TypeReference;
import util.reflect.InferUtil.TypeInferenceException;

public class InferUtilTest {

	@Test
	public void inferBasicSubclassFromClass() {
		Map<TypeVariable<?>, Type> result = InferUtil.infer(LinkedHashMap.class, ClassUtil.analyze(new TypeReference<HashMap<String, Integer>>() {}));
		assertEquals(2, result.size());
		assertEquals(String.class, result.get(LinkedHashMap.class.getTypeParameters()[0]));
		assertEquals(Integer.class, result.get(LinkedHashMap.class.getTypeParameters()[1]));
	}

	@Test
	public void inferBasicSubclassFromInterface() {
		Map<TypeVariable<?>, Type> result = InferUtil.infer(LinkedHashMap.class, ClassUtil.analyze(new TypeReference<Map<String, Integer>>() {}));
		assertEquals(2, result.size());
		assertEquals(String.class, result.get(LinkedHashMap.class.getTypeParameters()[0]));
		assertEquals(Integer.class, result.get(LinkedHashMap.class.getTypeParameters()[1]));
	}

	@Test
	public void inferBasicSubInterfaceFromInterface() {
		Map<TypeVariable<?>, Type> result = InferUtil.infer(ConcurrentMap.class, ClassUtil.analyze(new TypeReference<Map<String, Integer>>() {}));
		assertEquals(2, result.size());
		assertEquals(String.class, result.get(ConcurrentMap.class.getTypeParameters()[0]));
		assertEquals(Integer.class, result.get(ConcurrentMap.class.getTypeParameters()[1]));
	}

	@Test
	public void inferBasicSuperclassFromClass() {
		Map<TypeVariable<?>, Type> result = InferUtil.infer(AbstractMap.class, ClassUtil.analyze(new TypeReference<LinkedHashMap<String, Integer>>() {}));
		assertEquals(2, result.size());
		assertEquals(String.class, result.get(AbstractMap.class.getTypeParameters()[0]));
		assertEquals(Integer.class, result.get(AbstractMap.class.getTypeParameters()[1]));
	}

	@Test
	public void inferBasicSuperInterfaceFromClass() {
		Map<TypeVariable<?>, Type> result = InferUtil.infer(Map.class, ClassUtil.analyze(new TypeReference<LinkedHashMap<String, Integer>>() {}));
		assertEquals(2, result.size());
		assertEquals(String.class, result.get(Map.class.getTypeParameters()[0]));
		assertEquals(Integer.class, result.get(Map.class.getTypeParameters()[1]));
	}

	@Test
	public void inferBasicSuperInterfaceFromInterface() {
		Map<TypeVariable<?>, Type> result = InferUtil.infer(Map.class, ClassUtil.analyze(new TypeReference<ConcurrentMap<String, Integer>>() {}));
		assertEquals(String.class, result.get(Map.class.getTypeParameters()[0]));
		assertEquals(Integer.class, result.get(Map.class.getTypeParameters()[1]));
	}

	@Test
	public void inferBoundFromDeclaredUpperBoundWildcard() {
		Map<TypeVariable<?>, Type> result = InferUtil.infer(ArrayList.class, ClassUtil.analyze(new TypeReference<List<? extends Number>>() {}));
		assertEquals(1, result.size());
		WildcardType wildcardType = (WildcardType) result.get(ArrayList.class.getTypeParameters()[0]);
		assertArrayEquals(new Type[0], wildcardType.getLowerBounds());
		assertArrayEquals(new Type[] { Number.class }, wildcardType.getUpperBounds());
	}

	@Test
	public void inferNoUsefulInfoFromDeclaredLowerBoundWildcard() {
		Map<TypeVariable<?>, Type> result = InferUtil.infer(ArrayList.class, ClassUtil.analyze(new TypeReference<List<? super Number>>() {}));
		assertEquals(1, result.size());
		WildcardType wildcardType = (WildcardType) result.get(ArrayList.class.getTypeParameters()[0]);
		assertArrayEquals(new Type[] { Number.class }, wildcardType.getLowerBounds());
		assertArrayEquals(new Type[] { Object.class }, wildcardType.getUpperBounds());
	}


	private abstract static class MyStringList extends AbstractList<String> {
	}

	@Test(expected = TypeInferenceException.class)
	public void invalidDeclaredInterface() {
		InferUtil.infer(MyStringList.class, ClassUtil.analyze(new TypeReference<List<Number>>() {}));
	}

	@Test
	public void inferNothingForNonGenericClassFromValidDeclaredInterface() {
		Map<TypeVariable<?>, Type> result = InferUtil.infer(MyStringList.class, ClassUtil.analyze(new TypeReference<List<String>>() {}));
		assertEquals(0, result.size());
	}

	@Test(expected = TypeInferenceException.class)
	public void invalidTypeChainForClass() {
		InferUtil.infer(MyStringList.class, ClassUtil.analyze(new TypeReference<Map<String, Number>>() {}));
	}

	@Test(expected = TypeInferenceException.class)
	public void invalidTypeChainForInterface() {
		InferUtil.infer(Map.class, ClassUtil.analyze(new TypeReference<MyStringList>() {}));
	}


	private interface MyInterface<E> {
	}
	private static class MyComplexClass<A, B extends Number, M extends Map<A, B[][]>> implements MyInterface<M[]> {
	}
	private static class MyUpperBoundWildcardList<L extends List<? extends Number>> implements MyInterface<L> {
	}
	private static class MyLowerBoundWildcardList<L extends List<? super Number>> implements MyInterface<L> {
	}

	@Test
	public void inferUnboxedArrayTypesInTypeVariableBoundary() {
		Map<TypeVariable<?>, Type> result = InferUtil.infer(MyComplexClass.class, ClassUtil.analyze(new TypeReference<MyInterface<HashMap<String, int[][]>[]>>() {}));
		assertEquals(3, result.size());
		assertEquals(String.class, result.get(MyComplexClass.class.getTypeParameters()[0]));
		assertEquals(Integer.class, result.get(MyComplexClass.class.getTypeParameters()[1]));
		ParameterizedType type = (ParameterizedType) result.get(MyComplexClass.class.getTypeParameters()[2]);
		assertEquals(HashMap.class, type.getRawType());
		assertEquals(String.class, type.getActualTypeArguments()[0]);
		assertEquals(int[][].class, type.getActualTypeArguments()[1]);
		assertNull(type.getOwnerType());
	}

	@Test
	public void inferValidUpperBoundWildcardInTypeVariable() {
		Map<TypeVariable<?>, Type> result = InferUtil.infer(MyUpperBoundWildcardList.class, ClassUtil.analyze(new TypeReference<MyInterface<List<Integer>>>() {}));
		assertEquals(1, result.size());
		ParameterizedType type = (ParameterizedType) result.get(MyUpperBoundWildcardList.class.getTypeParameters()[0]);
		assertEquals(List.class, type.getRawType());
		assertEquals(Integer.class, type.getActualTypeArguments()[0]);
	}

	@Test(expected = TypeInferenceException.class)
	public void inferInvalidUpperBoundWildcardInTypeVariable() {
		InferUtil.infer(MyUpperBoundWildcardList.class, ClassUtil.analyze(new TypeReference<MyInterface<List<Object>>>() {}));
	}

	@Test
	public void inferValidLowerBoundWildcardInTypeVariable() {
		Map<TypeVariable<?>, Type> result = InferUtil.infer(MyLowerBoundWildcardList.class, ClassUtil.analyze(new TypeReference<MyInterface<List<Object>>>() {}));
		assertEquals(1, result.size());
		ParameterizedType type = (ParameterizedType) result.get(MyLowerBoundWildcardList.class.getTypeParameters()[0]);
		assertEquals(List.class, type.getRawType());
		assertEquals(Object.class, type.getActualTypeArguments()[0]);
	}

	@Test(expected = TypeInferenceException.class)
	public void inferInvalidLowerBoundWildcardInTypeVariable() {
		InferUtil.infer(MyLowerBoundWildcardList.class, ClassUtil.analyze(new TypeReference<MyInterface<List<Integer>>>() {}));
	}


	private static class MyConcreteA<A> implements MyInterface<List<? extends A>> {
	}
	private static class MyConcreteB<A, B extends List<? extends A>> implements MyInterface<B> {
	}
	private static class MyConcreteC<A, B extends List<? super A>> implements MyInterface<B> {
	}
	private static class MyConcreteD<A, B extends List<A>> implements MyInterface<B> {
	}

	@Test
	public void inferTypeVariableInWildcardBoundOfDeclaredType() {
		Map<TypeVariable<?>, Type> result = InferUtil.infer(MyConcreteA.class, ClassUtil.analyze(new TypeReference<MyInterface<List<? extends Number>>>() {}));
		assertEquals(1, result.size());
		assertEquals(Number.class, result.get(MyConcreteA.class.getTypeParameters()[0]));
	}

	@Test
	public void inferTypeVariableInWildcardUpperBoundOfTypeVariableBound() {
		@SuppressWarnings("unused")
		MyInterface<ArrayList<? extends Number>> myConcreteB = new MyConcreteB<Object, ArrayList<? extends Number>>();

		Map<TypeVariable<?>, Type> result = InferUtil.infer(MyConcreteB.class, ClassUtil.analyze(new TypeReference<MyInterface<ArrayList<? extends Number>>>() {}));
		assertEquals(2, result.size());
		// TypeVariable A can actually be any class equal to or superclass Number, as shown in the above myConcreteB declaration,
		// but this implementation should be good enough for most practical purposes
		assertEquals(Number.class, result.get(MyConcreteB.class.getTypeParameters()[0]));
		ParameterizedType type = (ParameterizedType) result.get(MyConcreteB.class.getTypeParameters()[1]);
		assertEquals(ArrayList.class, type.getRawType());
		assertArrayEquals(new Type[] { Number.class }, ((WildcardType) type.getActualTypeArguments()[0]).getUpperBounds());
	}

	@Test
	public void inferTypeVariableInWildcardLowerBoundOfTypeVariableBound() {
		@SuppressWarnings("unused")
		MyInterface<ArrayList<? super Number>> MyConcreteC = new MyConcreteC<Integer, ArrayList<? super Number>>();

		Map<TypeVariable<?>, Type> result = InferUtil.infer(MyConcreteC.class, ClassUtil.analyze(new TypeReference<MyInterface<ArrayList<? super Number>>>() {}));
		assertEquals(2, result.size());
		// TypeVariable A can actually be any class equal to or subclass Number, as shown in the above myConcreteC declaration,
		// but this implementation should be good enough for most practical purposes
		assertEquals(Number.class, result.get(MyConcreteC.class.getTypeParameters()[0]));
		ParameterizedType type = (ParameterizedType) result.get(MyConcreteC.class.getTypeParameters()[1]);
		assertEquals(ArrayList.class, type.getRawType());
		assertArrayEquals(new Type[] { Number.class }, ((WildcardType) type.getActualTypeArguments()[0]).getLowerBounds());
	}

	@Test
	public void inferTypeVariableInTypeVariableBound() {
		Map<TypeVariable<?>, Type> result = InferUtil.infer(MyConcreteD.class, ClassUtil.analyze(new TypeReference<MyInterface<ArrayList<Number>>>() {}));
		assertEquals(2, result.size());
		assertEquals(Number.class, result.get(MyConcreteD.class.getTypeParameters()[0]));
		ParameterizedType type = (ParameterizedType) result.get(MyConcreteD.class.getTypeParameters()[1]);
		assertEquals(ArrayList.class, type.getRawType());
		assertArrayEquals(new Type[] { Number.class }, type.getActualTypeArguments());
	}


	private static class Outer<A> {
		abstract class Inner<B> implements Map<A, B> {
		}
	}
	private static class Outer2<A> extends Outer<List<A>> {
		class Middle2 {
			abstract class Inner2<B> extends Inner<List<B>> {
			}
		}
	}
	private static class UseInner<A, B> implements MyInterface<Outer<A>.Inner<B>> {
	}
	private static class WildcardInner<A, B> implements MyInterface<List<? super Outer2<A>.Middle2.Inner2<B>>> {
	}
	private static class BoundedInner<A, B, C extends Outer<List<A>>.Inner<List<B>>> implements MyInterface<C> {
	}

	@Test
	public void inferBasicOuterClass() {
		Map<TypeVariable<?>, Type> result = InferUtil.infer(Outer.Inner.class, ClassUtil.analyze(new TypeReference<Map<String, Integer>>() {}));
		assertEquals(2, result.size());
		assertEquals(String.class, result.get(Outer.class.getTypeParameters()[0]));
		assertEquals(Integer.class, result.get(Outer.Inner.class.getTypeParameters()[0]));
	}

	@Test
	public void inferExtendedOuterClass() {
		Map<TypeVariable<?>, Type> result = InferUtil.infer(Outer2.Middle2.Inner2.class, ClassUtil.analyze(new TypeReference<Map<List<String>, List<Integer>>>() {}));
		assertEquals(2, result.size());
		assertEquals(String.class, result.get(Outer2.class.getTypeParameters()[0]));
		assertEquals(Integer.class, result.get(Outer2.Middle2.Inner2.class.getTypeParameters()[0]));
	}

	@Test
	public void inferDeclaredOuterClass() {
		Map<TypeVariable<?>, Type> result = InferUtil.infer(UseInner.class, ClassUtil.analyze(new TypeReference<MyInterface<Outer<String>.Inner<Integer>>>() {}));
		assertEquals(2, result.size());
		assertEquals(String.class, result.get(UseInner.class.getTypeParameters()[0]));
		assertEquals(Integer.class, result.get(UseInner.class.getTypeParameters()[1]));
	}

	@Test
	public void inferDeclaredWildcardOuterClass() {
		Map<TypeVariable<?>, Type> result = InferUtil.infer(WildcardInner.class, ClassUtil.analyze(new TypeReference<MyInterface<List<Outer<List<String>>.Inner<List<Integer>>>>>() {}));
		assertEquals(2, result.size());
		assertEquals(String.class, result.get(WildcardInner.class.getTypeParameters()[0]));
		assertEquals(Integer.class, result.get(WildcardInner.class.getTypeParameters()[1]));
	}

	@Test
	public void inferOuterClassInTypeVariableBound() {
		Map<TypeVariable<?>, Type> result = InferUtil.infer(BoundedInner.class, ClassUtil.analyze(new TypeReference<MyInterface<Outer2<String>.Middle2.Inner2<Integer>>>() {}));
		assertEquals(3, result.size());
		assertEquals(String.class, result.get(BoundedInner.class.getTypeParameters()[0]));
		assertEquals(Integer.class, result.get(BoundedInner.class.getTypeParameters()[1]));
		ParameterizedType type = (ParameterizedType) result.get(BoundedInner.class.getTypeParameters()[2]);
		assertEquals(Outer2.Middle2.Inner2.class, type.getRawType());
		assertArrayEquals(new Type[] { Integer.class }, type.getActualTypeArguments());
		ParameterizedType ownerType1 = (ParameterizedType) type.getOwnerType();
		assertEquals(Outer2.Middle2.class, ownerType1.getRawType());
		assertArrayEquals(new Type[0], ownerType1.getActualTypeArguments());
		ParameterizedType ownerType2 = (ParameterizedType) ownerType1.getOwnerType();
		assertEquals(Outer2.class, ownerType2.getRawType());
		assertArrayEquals(new Type[] { String.class }, ownerType2.getActualTypeArguments());
	}
}
