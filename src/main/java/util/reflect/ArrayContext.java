package util.reflect;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import util.reflect.ValueGenerator.CurrentInstanceContext;

/**
 * An ArrayContext represents an array class with its generic component type resolved.
 */
@SuppressWarnings("unchecked")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = {"actualClass", "componentType"}, callSuper = false)
public class ArrayContext<T> extends ContextualType<T> {

	private static final Map<Type, ArrayContext<?>> CACHE = new ConcurrentHashMap<>();

	private final Type originalType;

	private Type resolvedType;

	@Getter
	private final Class<T> actualClass;
	@Getter
	private final ContextualType<?> componentType;

	static <R> ArrayContext<R> ofClass(Class<R> clazz) {
		ArrayContext<R> cached = (ArrayContext<R>) CACHE.get(clazz);
		if (cached != null) {
			return cached;
		}
		return new ArrayContext<>(clazz, clazz, ClassUtil.analyze(clazz.getComponentType()));
	}

	static <R> ArrayContext<R> ofGenericArrayType(GenericArrayType type) {
		ArrayContext<R> cached = (ArrayContext<R>) CACHE.get(type);
		if (cached != null) {
			return cached;
		}
		Type componentType = type.getGenericComponentType();
		return new ArrayContext<>(
				type,
				(Class<R>) Array.newInstance(ClassUtil.toBasicClass(componentType), 0).getClass(),
				ClassUtil.analyze(componentType));
	}

	public Type getResolvedType() {
		if (resolvedType == null) {
			resolvedType = TypeResolver.newResolvedArrayType(componentType.getResolvedType());
		}
		return resolvedType;
	}

	public ArrayContext<T> intern() {
		return (ArrayContext<T>) CACHE.computeIfAbsent(originalType, t -> this);
	}

	/**
	 * Create a new array instance of the given length.
	 */
	public T newInstance(int length) {
		return (T) Array.newInstance(actualClass.getComponentType(), length);
	}

	protected T randomInstance(ValueGenerator generator, RandomInstanceState state) throws ReflectiveOperationException {
		int length = generator.getCollectionSize(this, state.joinPath());
		T instance = newInstance(length);
		for (int i = 0; i < length; i++) {
			state.pushIndexPath(i);
			CurrentInstanceContext instanceCreator = () -> {
				return generator.generate(
						componentType,
						state.joinPath(),
						() -> componentType.randomInstance(generator, state));
			};
			List<Object> recursed = state.getInstances(componentType);
			Object item;
			if (recursed.size() > 0) {
				item = generator.onRecursion(componentType, state.joinPath(), recursed, instanceCreator);
			} else {
				item = instanceCreator.randomInstance();
			}
			Array.set(instance, i, item);
			state.popPath();
		}
		return instance;
	}

	@Override
	public String toString() {
		return componentType + "[]";
	}
}
