package util.reflect;

import java.lang.reflect.Type;

import util.reflect.ValueGenerator.DefaultValueGenerator;

public abstract class ContextualType<T> {

	public abstract Class<T> getActualClass();

	public abstract Type getResolvedType();

	public T randomInstance() throws ReflectiveOperationException {
		return randomInstance(new DefaultValueGenerator(), new RandomInstanceState());
	}

	/**
	 * Create a new instance with randomly initialized fields (or items for ArrayContext).
	 */
	public T randomInstance(ValueGenerator generator) throws ReflectiveOperationException {
		return randomInstance(generator, new RandomInstanceState());
	}

	protected abstract T randomInstance(ValueGenerator generator, RandomInstanceState state) throws ReflectiveOperationException;

	/**
	 * Cache the given ContextualType. Analyzing the same Class/Type will return the same ContextualType instance from the cache.
	 */
	public abstract <S extends ContextualType<T>> S intern();
}
