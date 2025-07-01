package util.reflect;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public interface ValueGenerator {

	/**
	 * Implementation of this method should return true if the given Field, declared in the declaringType, at the given path (formed by concatenating field names and array indices), should be ignored.
	 * This is useful in ignoring internal fields of some classes, like the private field "size" of an ArrayList.
	 */
	boolean isIgnoredField(ContextualType<?> declaringType, String path, Field field);

	/**
	 * Implementation of this method should return a concrete implementation class for the given ContextualType, at the given path.
	 */
	Class<?> getImplementationClass(ContextualType<?> type, String path);

	/**
	 * Implementation of this method should return an instance for the given ContextualType, at the given path (formed by concatenating field names and array indices).
	 * If default random instance behaviour is desired, call {@link CurrentInstanceContext#randomInstance()} and return the instance.
	 */
	Object generate(ContextualType<?> type, String path, CurrentInstanceContext currentInstanceContext) throws ReflectiveOperationException;

	/**
	 * If there is any circular reference formed (e.g. class A containing a field referencing class A), parent instances of the same ContextualType can be found in the recursed Object List.
	 * If default random instance behaviour is desired, call {@link CurrentInstanceContext#randomInstance()} and return the instance.
	 * Do not call {@link CurrentInstanceContext#randomInstance()} in order to break the recursion.
	 */
	Object onRecursion(ContextualType<?> type, String path, List<Object> recursed, CurrentInstanceContext currentInstanceContext) throws ReflectiveOperationException;

	/**
	 * Implementation of this method should return the desired array/collection size for the given ContextualType, at the given path (formed by concatenating field names and array indices).
	 */
	int getCollectionSize(ContextualType<?> type, String path);


	@FunctionalInterface
	public interface CurrentInstanceContext {
		Object randomInstance() throws ReflectiveOperationException;
	}


	/**
	 * It is recommended to extend this class (instead of implementing the ValueGenerator interface from scratch) for customized random instance behaviour.
	 */
	public static class DefaultValueGenerator implements ValueGenerator {

		protected long MIN_TIMESTAMP = LocalDateTime.of(1800, 1, 1, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli();
		protected long MAX_TIMESTAMP = LocalDateTime.of(2199, 12, 31, 23, 59, 59, 999999999).toInstant(ZoneOffset.UTC).toEpochMilli();

		protected int MIN_COLLECTION_SIZE = 3;
		protected int MAX_COLLECTION_SIZE = 5;

		protected Random random;

		public DefaultValueGenerator() {
			this(new Random());
		}

		public DefaultValueGenerator(Random random) {
			this.random = random == null ? new Random() : random;
		}

		protected Instant randomTimeInstant() {
			return Instant.ofEpochMilli(MIN_TIMESTAMP + (long)(random.nextDouble() * (MAX_TIMESTAMP - MIN_TIMESTAMP + 1)));
		}

		protected ZoneId randomTimeZone() {
			List<String> zones = new ArrayList<>(ZoneId.getAvailableZoneIds());
			return ZoneId.of(zones.get(random.nextInt(zones.size())));
		}

		protected Class<?> randomClass() {
			List<Class<?>> classes = List.of(int.class, Integer.class, int[].class, Integer[].class, Object.class);
			return classes.get(random.nextInt(classes.size()));
		}

		@Override
		public boolean isIgnoredField(ContextualType<?> declaringType, String path, Field field) {
			String packageName = field.getDeclaringClass().getPackageName();
			return packageName.startsWith("java.") || packageName.startsWith("javax.");
		}

		@Override
		public Class<?> getImplementationClass(ContextualType<?> type, String path) {
			Class<?> clazz = type.getActualClass();
			if (clazz == List.class) return ArrayList.class;
			if (clazz == Collection.class) return ArrayList.class;
			if (clazz == Set.class) return LinkedHashSet.class;
			if (clazz == Map.class) return LinkedHashMap.class;
			return clazz;
		}

		@SuppressWarnings("unchecked")
		@Override
		public Object generate(ContextualType<?> type, String path, CurrentInstanceContext currentInstanceContext) throws ReflectiveOperationException {
			Class<?> clazz = type.getActualClass();

			if (clazz == void.class || clazz == Void.class) return null;
			if (clazz == boolean.class || clazz == Boolean.class) return random.nextBoolean();
			if (clazz == byte.class || clazz == Byte.class) return (byte) random.nextInt();
			if (clazz == char.class || clazz == Character.class) return (char) random.nextInt();
			if (clazz == short.class || clazz == Short.class) return (short) random.nextInt();
			if (clazz == int.class || clazz == Integer.class) return random.nextInt();
			if (clazz == long.class || clazz == Long.class) return random.nextLong();
			if (clazz == float.class || clazz == Float.class) return random.nextFloat();
			if (clazz == double.class || clazz == Double.class) return random.nextDouble();

			if (clazz == String.class) {
				byte[] bytes = new byte[16];
				random.nextBytes(bytes);
				return UUID.nameUUIDFromBytes(bytes).toString();
			}

			if (clazz.isEnum()) {
				Object[] enums = clazz.getEnumConstants();
				return enums.length == 0 ? null : enums[random.nextInt(enums.length)];
			}
			if (clazz == Enum.class) {
				ClassContext<Enum<?>> enumClassContext = (ClassContext<Enum<?>>) type;
				Class<?> enumClass = (Class<?>) enumClassContext.getContext().get(Enum.class.getTypeParameters()[0]);
				Object[] enums = enumClass.getEnumConstants();
				return enums.length == 0 ? null : enums[random.nextInt(enums.length)];
			}

			if (clazz == Date.class) return Date.from(randomTimeInstant());
			if (clazz == Instant.class) return randomTimeInstant();
			if (clazz == LocalDate.class) return LocalDate.ofInstant(randomTimeInstant(), ZoneOffset.UTC);
			if (clazz == LocalTime.class) return LocalTime.ofInstant(randomTimeInstant(), ZoneOffset.UTC);
			if (clazz == LocalDateTime.class) return LocalDateTime.ofInstant(randomTimeInstant(), ZoneOffset.UTC);
			if (clazz == OffsetTime.class) return OffsetTime.ofInstant(randomTimeInstant(), randomTimeZone());
			if (clazz == OffsetDateTime.class) return OffsetDateTime.ofInstant(randomTimeInstant(), randomTimeZone());
			if (clazz == ZonedDateTime.class) return ZonedDateTime.ofInstant(randomTimeInstant(), randomTimeZone());

			if (clazz == Class.class) return randomClass();

			return currentInstanceContext.randomInstance();
		}

		@Override
		public Object onRecursion(ContextualType<?> type, String path, List<Object> recursed, CurrentInstanceContext currentInstanceContext) throws ReflectiveOperationException {
			return null;
		}

		@Override
		public int getCollectionSize(ContextualType<?> type, String path) {
			return MIN_COLLECTION_SIZE + (int)(random.nextDouble() * (MAX_COLLECTION_SIZE - MIN_COLLECTION_SIZE + 1));
		}
	}
}
