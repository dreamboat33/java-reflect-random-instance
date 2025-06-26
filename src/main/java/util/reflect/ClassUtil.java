package util.reflect;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import util.reflect.ClassUtil.CloneOptions.DefaultCloneOptions;

@SuppressWarnings("unchecked")
public class ClassUtil {

	public static final Map<Class<?>, Object> PRIMITIVE_DEFAULTS;
	static {
		Map<Class<?>, Object> map = new HashMap<>();
		map.put(boolean.class, false);
		map.put(byte.class, (byte) 0);
		map.put(char.class, (char) 0);
		map.put(short.class, (short) 0);
		map.put(int.class, 0);
		map.put(long.class, 0l);
		map.put(float.class, 0.f);
		map.put(double.class, 0.);
		PRIMITIVE_DEFAULTS = Collections.unmodifiableMap(map);
	}

	static {
		for (Class<?> primitive : PRIMITIVE_DEFAULTS.keySet()) {
			analyze(primitive).intern();
			analyze(box(primitive)).intern();
		}
		for (Class<?> clazz : List.of(String.class, Date.class,
				Instant.class, LocalDate.class, LocalTime.class, LocalDateTime.class, OffsetTime.class, OffsetDateTime.class, ZonedDateTime.class)) {
			analyze(clazz).intern();
		}
	}

	public static abstract class TypeReference<T> {
		private final Type type;
		protected TypeReference() {
			if (this.getClass().getGenericSuperclass() instanceof ParameterizedType theType && theType.getRawType() == TypeReference.class) {
				type = theType.getActualTypeArguments()[0];
			} else {
				throw new IllegalStateException("Direct subclass of TypeReference expected.");
			}
		}
		public final Type getType() {
			return type;
		}
	}

	public static <T> T getOuterInstance(Object object) throws ReflectiveOperationException {
		List<Field> outerInstanceFields = Arrays.stream(object.getClass().getDeclaredFields()).filter(Field::isSynthetic).filter(field -> field.getName().startsWith("this$")).toList();
		if (outerInstanceFields.size() == 0) {
			return null;
		}
		if (outerInstanceFields.size() > 1) {
			throw new IllegalStateException("Unable to determine the synthetic field for outer instance");
		}
		Field outerInstanceField = outerInstanceFields.get(0);
		outerInstanceField.setAccessible(true);
		return (T) outerInstanceField.get(object);
	}

	public static boolean isInnerClass(Class<?> clazz) {
		return clazz.getEnclosingClass() != null;
	}

	public static boolean isStaticClass(Class<?> clazz) {
		return Modifier.isStatic(clazz.getModifiers());
	}

	public static <T> Class<T> toBasicClass(Type type) {
		if (type instanceof Class<?> clazz) {
			return (Class<T>) clazz;
		}
		if (type instanceof ParameterizedType theType) {
			return toBasicClass(theType.getRawType());
		}
		if (type instanceof GenericArrayType theType) {
			return (Class<T>) Array.newInstance(toBasicClass(theType.getGenericComponentType()), 0).getClass();
		}
		if (type instanceof TypeVariable<?> theType) {
			return toBasicClass(getBound(theType));
		}
		if (type instanceof WildcardType theType) {
			return toBasicClass(getBound(theType));
		}
		throw new IllegalArgumentException("Unknown supported type " + type);
	}

	public static Type getBound(Type type) {
		if (type instanceof TypeVariable<?> theType) {
			return getBound(theType.getBounds()[0]);
		}
		if (type instanceof WildcardType theType) {
			return getBound(theType.getLowerBounds().length > 0 ? theType.getLowerBounds()[0] : theType.getUpperBounds()[0]);
		}
		return type;
	}

	public static Class<?> box(Class<?> clazz) {
		if (clazz == void.class) return Void.class;
		if (clazz == boolean.class) return Boolean.class;
		if (clazz == byte.class) return Byte.class;
		if (clazz == char.class) return Character.class;
		if (clazz == short.class) return Short.class;
		if (clazz == int.class) return Integer.class;
		if (clazz == long.class) return Long.class;
		if (clazz == float.class) return Float.class;
		if (clazz == double.class) return Double.class;
		if (clazz.isArray()) {
			Class<?> c = clazz;
			int dimension = 0;
			while (c.isArray()) {
				c = c.getComponentType();
				dimension++;
			}
			Class<?> boxed = box(c);
			return boxed == c ? clazz : Array.newInstance(boxed, new int[dimension]).getClass();
		}
		return clazz;
	}

	public static Class<?> unbox(Class<?> clazz) {
		if (clazz == Void.class) return void.class;
		if (clazz == Boolean.class) return boolean.class;
		if (clazz == Byte.class) return byte.class;
		if (clazz == Character.class) return char.class;
		if (clazz == Short.class) return short.class;
		if (clazz == Integer.class) return int.class;
		if (clazz == Long.class) return long.class;
		if (clazz == Float.class) return float.class;
		if (clazz == Double.class) return double.class;
		if (clazz.isArray()) {
			Class<?> c = clazz;
			int dimension = 0;
			while (c.isArray()) {
				c = c.getComponentType();
				dimension++;
			}
			Class<?> unboxed = unbox(c);
			return unboxed == c || unboxed == void.class ? clazz : Array.newInstance(unboxed, new int[dimension]).getClass();
		}
		return clazz;
	}

	public static <S, T extends ContextualType<S>> T analyze(Class<S> clazz) {
		if (clazz.isArray()) {
			return (T) ArrayContext.ofClass(clazz);
		}
		return (T) ClassContext.ofClass(clazz);
	}

	public static <T extends ContextualType<?>> T analyze(Type type) {
		if (type instanceof Class<?> clazz) {
			return (T) analyze(clazz);
		}
		if (type instanceof GenericArrayType theType) {
			return (T) ArrayContext.ofGenericArrayType(theType);
		}
		if (type instanceof ParameterizedType theType) {
			return (T) ClassContext.ofParameterizedType(theType);
		}
		if (type instanceof TypeVariable<?> || type instanceof WildcardType) {
			return analyze(getBound(type));
		}
		throw new IllegalArgumentException("Unrecognized type " + type);
	}

	public static <S, T extends ContextualType<S>> T analyze(TypeReference<S> typeReference) {
		return analyze(typeReference.getType());
	}


	public interface CloneOptions {
		/**
		 * Objects of reassignable classes will not be cloned.
		 */
		boolean isReassignable(Class<?> clazz);
		/**
		 * Implementation of this method should return true if the given Field should be ignored.
		 * This is useful in ignoring internal fields of some classes, like the private field "size" of an ArrayList.
		 */
		boolean isIgnoredField(Field field);

		public static class DefaultCloneOptions implements CloneOptions {
			public boolean isReassignable(Class<?> clazz) {
				if (clazz == void.class || clazz == Void.class) return true;
				if (clazz == boolean.class || clazz == Boolean.class) return true;
				if (clazz == byte.class || clazz == Byte.class) return true;
				if (clazz == char.class || clazz == Character.class) return true;
				if (clazz == short.class || clazz == Short.class) return true;
				if (clazz == int.class || clazz == Integer.class) return true;
				if (clazz == long.class || clazz == Long.class) return true;
				if (clazz == float.class || clazz == Float.class) return true;
				if (clazz == double.class || clazz == Double.class) return true;
				if (clazz == String.class) return true;
				if (clazz.isEnum() || clazz == Enum.class) return true;

				if (clazz == Date.class) return true;
				if (clazz == Instant.class) return true;
				if (clazz == LocalDate.class) return true;
				if (clazz == LocalTime.class) return true;
				if (clazz == LocalDateTime.class) return true;
				if (clazz == OffsetTime.class) return true;
				if (clazz == OffsetDateTime.class) return true;
				if (clazz == ZonedDateTime.class) return true;

				if (clazz == Class.class) return true;
				return false;
			}
			public boolean isIgnoredField(Field field) {
				String packageName = field.getDeclaringClass().getPackageName();
				return packageName.startsWith("java.") || packageName.startsWith("javax.");
			}
		}
	}

	public static <T> T shallowClone(T object) throws ReflectiveOperationException {
		return shallowClone(object, new DefaultCloneOptions());
	}

	public static <T> T shallowClone(T object, CloneOptions options) throws ReflectiveOperationException {
		if (object == null) return null;

		Class<?> clazz = object.getClass();
		if (options.isReassignable(clazz)) {
			return object;
		}

		if (clazz.isArray()) {
			int length = Array.getLength(object);
			Object clone = Array.newInstance(clazz.getComponentType(), length);
			for (int i = 0; i < length; i++) {
				Array.set(clone, i, Array.get(object, i));
			}
			return (T) clone;
		}

		Object outer = getOuterInstance(object);
		ClassContext<?> classContext = (ClassContext<?>) analyze(clazz).intern();
		Object clone = classContext.newInstance(outer);
		for (Field field : classContext.getFieldTypes().keySet()) {
			if (options.isIgnoredField(classContext.fields.get(field.getName()))) {
				continue;
			}
			field.set(clone, field.get(object));
		}

		if (object instanceof Collection<?> collection) {
			((Collection<Object>) clone).addAll(collection);
		}
		if (object instanceof Map<?, ?> map) {
			((Map<Object, Object>) clone).putAll(map);
		}
		return (T) clone;
	}

	public static <T> T deepClone(T object) throws ReflectiveOperationException {
		return deepClone(object, new DefaultCloneOptions(), new IdentityHashMap<>());
	}

	public static <T> T deepClone(T object, CloneOptions options) throws ReflectiveOperationException {
		return deepClone(object, options, new IdentityHashMap<>());
	}

	private static <T> T deepClone(T object, CloneOptions options, IdentityHashMap<Object, Object> clones) throws ReflectiveOperationException {
		if (object == null) return null;

		Class<?> clazz = object.getClass();
		if (options.isReassignable(clazz)) {
			return object;
		}

		if (clones.containsKey(object)) {
			return (T) clones.get(object);
		}

		if (clazz.isArray()) {
			int length = Array.getLength(object);
			Object clone = Array.newInstance(clazz.getComponentType(), length);
			clones.put(object, clone);
			for (int i = 0; i < length; i++) {
				Array.set(clone, i, deepClone(Array.get(object, i), options, clones));
			}
			return (T) clone;
		}

		Object outer = getOuterInstance(object);
		Object outerClone = null;
		if (outer != null) {
			outerClone = deepClone(outer, options,clones);
		}

		ClassContext<?> classContext = (ClassContext<?>) analyze(clazz).intern();
		Object clone = classContext.newInstance(outerClone);
		clones.put(object, clone);
		for (Field field : classContext.getFieldTypes().keySet()) {
			if (options.isIgnoredField(classContext.fields.get(field.getName()))) {
				continue;
			}
			field.set(clone, deepClone(field.get(object), options, clones));
		}

		if (object instanceof Collection<?> collection) {
			for (Object item : collection) {
				((Collection<Object>) clone).add(deepClone(item, options, clones));
			}
		}
		if (object instanceof Map<?, ?> map) {
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				((Map<Object, Object>) clone).put(deepClone(entry.getKey(), options, clones), deepClone(entry.getValue(), options, clones));
			}
		}
		return (T) clone;
	}
}
