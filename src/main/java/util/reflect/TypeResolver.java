package util.reflect;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Map;
import java.util.stream.Stream;

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

public class TypeResolver {

	/**
	 * Resolve the TypeVariables referenced by the Type type
	 */
	public static Type resolve(Type type, Map<TypeVariable<?>, Type> map) {
		if (type instanceof Class<?> || type instanceof ResolvedType) {
			return type;
		}
		if (type instanceof GenericArrayType theType) {
			return new ResolvedGenericArrayType(
					resolve(theType.getGenericComponentType(), map));
		}
		if (type instanceof ParameterizedType theType) {
			return new ResolvedParameterizedType(
					resolve(theType.getActualTypeArguments(), map),
					resolve(theType.getRawType(), map),
					resolve(theType.getOwnerType(), map));
		}
		if (type instanceof WildcardType theType) {
			return new ResolvedWildcardType(
					resolve(theType.getUpperBounds(), map),
					resolve(theType.getLowerBounds(), map));
		}
		if (type instanceof TypeVariable<?> theType) {
			Type resolvedType = map.get(theType);
			if (resolvedType != null && resolvedType != theType) {
				return resolve(resolvedType, map);
			}
			return new BoundsResolvedTypeVariable<>(
					theType,
					resolve(theType.getBounds(), map));
		}
		return type;
	}

	public static Type[] resolve(Type[] types, Map<TypeVariable<?>, Type> map) {
		return Stream.of(types).map(type -> resolve(type, map)).toArray(Type[]::new);
	}

	private interface ResolvedType {}

	@RequiredArgsConstructor
	@EqualsAndHashCode
	private static class ResolvedGenericArrayType implements GenericArrayType, ResolvedType {
		private final Type componentType;
		@Override
		public Type getGenericComponentType() {
			return componentType;
		}
		@Override
		public String toString() {
			return componentType + "[]";
		}
	}

	@RequiredArgsConstructor
	@EqualsAndHashCode
	private static class ResolvedParameterizedType implements ParameterizedType, ResolvedType {
		private final Type[] actuals;
		private final Type raw, owner;
		@Override
		public Type[] getActualTypeArguments() {
			return actuals == null ? null : actuals.clone();
		}
		@Override
		public Type getRawType() {
			return raw;
		}
		@Override
		public Type getOwnerType() {
			return owner;
		}
		@Override
		public String toString() {
			return (owner == null ? "" : owner + ".") + raw + (actuals == null ? "" : "<" + String.join(", ", Stream.of(actuals).map(Type::toString).toList()) + ">");
		}
	}

	@RequiredArgsConstructor
	@EqualsAndHashCode
	private static class ResolvedWildcardType implements WildcardType, ResolvedType {
		private final Type[] upper, lower;
		@Override
		public Type[] getUpperBounds() {
			return upper.clone();
		}
		@Override
		public Type[] getLowerBounds() {
			return lower.clone();
		}
		@Override
		public String toString() {
			return "?" + (lower.length == 0 ? "" : " super " + lower[0]) + (upper[0] == Object.class ? "" : " extends " + upper[0]);
		}
	}

	@EqualsAndHashCode(of = {"original", "bounds"})
	private static class BoundsResolvedTypeVariable<D extends GenericDeclaration> implements TypeVariable<D>, ResolvedType {
		private final TypeVariable<D> original;
		private final Type[] bounds;
		private final ResolvedAnnotatedType[] annotatedBounds;
		public BoundsResolvedTypeVariable(TypeVariable<D> original, Type[] bounds) {
			this.original = original;
			this.bounds = bounds;

			AnnotatedType[] annotatedBounds = original.getAnnotatedBounds();
			ResolvedAnnotatedType[] resolvedAnnotatedBounds = new ResolvedAnnotatedType[bounds.length];
			for (int i = 0; i < resolvedAnnotatedBounds.length; i++) {
				resolvedAnnotatedBounds[i] = new ResolvedAnnotatedType(annotatedBounds[i], bounds[i]);
			}
			this.annotatedBounds = resolvedAnnotatedBounds;
		}
		@Override
		public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
			return original.getAnnotation(annotationClass);
		}
		@Override
		public Annotation[] getAnnotations() {
			return original.getAnnotations();
		}
		@Override
		public Annotation[] getDeclaredAnnotations() {
			return original.getDeclaredAnnotations();
		}
		@Override
		public Type[] getBounds() {
			return bounds.clone();
		}
		@Override
		public D getGenericDeclaration() {
			return original.getGenericDeclaration();
		}
		@Override
		public String getName() {
			return original.getName();
		}
		@Override
		public AnnotatedType[] getAnnotatedBounds() {
			return annotatedBounds.clone();
		}
		@Override
		public String toString() {
			return getName() + (bounds.length == 0 || bounds[0] == Object.class ? "" : " extends " + bounds[0]);
		}
	}

	@RequiredArgsConstructor
	@EqualsAndHashCode
	private static class ResolvedAnnotatedType implements AnnotatedType {
		private final AnnotatedType original;
		private final Type type;
		@Override
		public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
			return original.getAnnotation(annotationClass);
		}
		@Override
		public Annotation[] getAnnotations() {
			return original.getAnnotations();
		}
		@Override
		public Annotation[] getDeclaredAnnotations() {
			return ((AnnotatedType) original).getDeclaredAnnotations();
		}
		@Override
		public Type getType() {
			return type;
		}
	}

	/**
	 * Return a ParameterizedType for the given Class and the given generic TypeVariable map.
	 * If the given Class does not declare any generic type, the Class will be returned.
	 */
	public static Type newResolvedClassType(Class<?> clazz, Map<TypeVariable<?>, Type> map) {
		Type[] actuals = Stream.of(clazz.getTypeParameters()).map(param -> map.getOrDefault(param, param)).toArray(Type[]::new);
		if (ClassUtil.isInnerClass(clazz)) {
			Type resolvedOuterClass = newResolvedClassType(clazz.getEnclosingClass(), map);
			return actuals.length == 0 && resolvedOuterClass instanceof Class ? clazz : new ParameterizedTypeImpl(actuals, clazz, resolvedOuterClass);
		}
		return actuals.length == 0 ? clazz : new ParameterizedTypeImpl(actuals, clazz, null);
	}

	@RequiredArgsConstructor
	@EqualsAndHashCode
	private static class ParameterizedTypeImpl implements ParameterizedType {
		private final Type[] actuals;
		private final Type raw, owner;
		@Override
		public Type[] getActualTypeArguments() {
			return actuals == null ? null : actuals.clone();
		}
		@Override
		public Type getRawType() {
			return raw;
		}
		@Override
		public Type getOwnerType() {
			return owner;
		}
		@Override
		public String toString() {
			return (owner == null ? "" : owner + ".") + raw + (actuals == null ? "" : "<" + String.join(", ", Stream.of(actuals).map(Type::toString).toList()) + ">");
		}
	}

	/**
	 * Return a GenericArrayType for the given component Type.
	 * If the given type does not contain any generic type, a Class representing the array type will be returned instead.
	 */
	public static Type newResolvedArrayType(Type componentType) {
		if (componentType instanceof Class<?> clazz) {
			return Array.newInstance(clazz, 0).getClass();
		}
		return new GenericArrayTypeImpl(componentType);
	}

	@RequiredArgsConstructor
	@EqualsAndHashCode
	private static class GenericArrayTypeImpl implements GenericArrayType {
		private final Type componentType;
		@Override
		public Type getGenericComponentType() {
			return componentType;
		}
		@Override
		public String toString() {
			return componentType + "[]";
		}
	}
}
