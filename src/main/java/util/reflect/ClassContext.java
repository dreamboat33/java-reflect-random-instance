package util.reflect;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import util.reflect.ValueGenerator.CurrentInstanceContext;

/**
 * A ClassContext represents a class with its generic types resolved.
 */
@SuppressWarnings("unchecked")
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@EqualsAndHashCode(of = {"actualClass", "context"}, callSuper = false)
public class ClassContext<T> extends ContextualType<T> {

	private static final Map<Type, ClassContext<?>> CACHE = new ConcurrentHashMap<>();

	private final Type originalType;

	private Type resolvedType;

	@Getter
	private final Class<T> actualClass;
	@Getter
	private final Map<TypeVariable<?>, Type> context;
	private final ClassContext<?> outerClass;

	private Optional<ClassContext<?>> superclass;
	private Map<Class<?>, ClassContext<?>> interfaces;

	private Optional<Constructor<T>> newInstanceConstructor;
	private Object[] newInstanceArgs;

	private final Map<Class<?>, ClassContext<?>> inferredImplementation = new HashMap<>();

	// Field values in this map will be exposed to outside, do not use internally for reflection purposes
	Map<String, Field> fields;
	// Field keys in this map have accessible set to true and should be used internally for reflection purposes
	Map<Field, ContextualType<?>> fieldTypes;

	static <R> ClassContext<R> ofParameterizedType(ParameterizedType parameterizedType) {
		ClassContext<R> cached = (ClassContext<R>) CACHE.get(parameterizedType);
		if (cached != null) {
			return cached;
		}

		Class<R> clazz = (Class<R>) parameterizedType.getRawType();
		TypeVariable<Class<R>>[] typeParams = clazz.getTypeParameters();
		Type[] actualTypes = parameterizedType.getActualTypeArguments();

		Map<TypeVariable<?>, Type> typeVariableMap = new HashMap<>();
		ClassContext<?> outerClass = null;
		if (ClassUtil.isInnerClass(clazz)) {
			outerClass = ofType(parameterizedType.getOwnerType());
			if (!ClassUtil.isStaticClass(clazz)) {
				typeVariableMap.putAll(outerClass.context);
			}
		}
		for (int i = 0; i < typeParams.length; i++) {
			Type actualType = actualTypes[i];
			typeVariableMap.put((TypeVariable<Class<?>>) (TypeVariable<?>) typeParams[i], actualType);
		}
		return new ClassContext<>(parameterizedType, clazz, Collections.unmodifiableMap(typeVariableMap), outerClass);
	}

	static <R> ClassContext<R> ofClass(Class<R> clazz) {
		ClassContext<R> cached = (ClassContext<R>) CACHE.get(clazz);
		if (cached != null) {
			return cached;
		}
		return new ClassContext<>(clazz, clazz, Collections.emptyMap(), ClassUtil.isInnerClass(clazz) ? ofClass(clazz.getEnclosingClass()) : null);
	}

	static <R> ClassContext<R> ofType(Type type) {
		if (type instanceof ParameterizedType theType) {
			return ofParameterizedType(theType);
		}
		return ofClass((Class<R>) type);
	}

	private <R> ClassContext<R> of(Type type) {
		if (type == null) {
			return null;
		}
		if (type instanceof ParameterizedType theType) {
			return ofParameterizedType((ParameterizedType) TypeResolver.resolve(theType, context));
		}
		return ofClass((Class<R>) type);
	}

	public Type getResolvedType() {
		if (resolvedType == null) {
			resolvedType = TypeResolver.newResolvedClassType(actualClass, context);
		}
		return resolvedType;
	}

	public ClassContext<T> intern() {
		return (ClassContext<T>) CACHE.computeIfAbsent(originalType, t -> this);
	}

	public <R> ClassContext<R> getOuterClass() {
		return (ClassContext<R>) outerClass;
	}

	public <R> ClassContext<R> getSuperclass() {
		if (superclass == null) {
			superclass = Optional.ofNullable(of(actualClass.getGenericSuperclass()));
		}
		return (ClassContext<R>) superclass.orElse(null);
	}

	private Map<Class<?>, ClassContext<?>> getInterfaceMap() {
		if (interfaces == null) {
			LinkedHashMap<Class<?>, ClassContext<?>> result = new LinkedHashMap<>();
			for (Type genericInterface : actualClass.getGenericInterfaces()) {
				ClassContext<?> interfaceContext = of(genericInterface);
				result.put(interfaceContext.getActualClass(), interfaceContext);
				result.putAll(interfaceContext.getInterfaceMap());
			}
			if (getSuperclass() != null) {
				result.putAll(getSuperclass().getInterfaceMap());
			}
			interfaces = Collections.unmodifiableMap(result);
		}
		return interfaces;
	}

	/**
	 * If this ClassContext implements the given interface anywhere up its super type chain, the ClassContext of the interfaceClass will be returned. Otherwise null is returned.
	 */
	public <R> ClassContext<R> getInterface(Class<R> interfaceClass) {
		return (ClassContext<R>) getInterfaceMap().get(interfaceClass);
	}

	/**
	 * Get all the interfaces that this ClassContext implements, including all interfaces implemented up its super type chain.
	 */
	public ClassContext<?>[] getInterfaces() {
		return getInterfaceMap().values().toArray(ClassContext<?>[]::new);
	}


	private void computeConstructor() throws ReflectiveOperationException {
		if (newInstanceConstructor != null) {
			return;
		}
		for (Constructor<T> constructor : (Constructor<T>[]) actualClass.getDeclaredConstructors()) {
			if (constructor.trySetAccessible()) {
				try {
					Object[] args = Stream.of(constructor.getParameterTypes())
							.map(paramType -> ClassUtil.PRIMITIVE_DEFAULTS.get(paramType))
							.toArray(Object[]::new);
					constructor.newInstance(args);
					newInstanceConstructor = Optional.of(constructor);
					newInstanceArgs = args;
					return;
				} catch (ReflectiveOperationException e) {
				}
			}
		}
		newInstanceConstructor = Optional.ofNullable(null);
	}

	/**
	 * Create a new instance from any of its constructors. An InstantiationException is thrown if fails.
	 */
	public T newInstance() throws ReflectiveOperationException {
		return newInstance(null);
	}

	/**
	 * Create a new instance from any of its constructors. An InstantiationException is thrown if fails.
	 * If this ClassContext requires an enclosing instance, the given outerInstance will be used, or one will be generated if the given outerInstance is null.
	 */
	public T newInstance(Object outerInstance) throws ReflectiveOperationException {
		computeConstructor();
		if (newInstanceConstructor.isEmpty()) {
			throw new InstantiationException("Cannot find a suitable constructor for class " + actualClass.getName());
		}
		if (outerClass == null || ClassUtil.isStaticClass(actualClass)) {
			if (outerInstance != null) {
				throw new IllegalArgumentException("Class " + actualClass.getName() + " does not require an enclosing instance");
			}
			return newInstanceConstructor.get().newInstance(newInstanceArgs);
		}
		Object[] args = newInstanceArgs.clone();
		args[0] = outerInstance != null ? outerInstance : outerClass.newInstance();
		return newInstanceConstructor.get().newInstance(args);
	}

	private Field copyField(Field field) {
		try {
			return field.getDeclaringClass().getDeclaredField(field.getName());
		} catch (NoSuchFieldException e) {
			// swallowing the exception since this is impossible
			return null;
		}
	}

	private void computeFields() {
		if (fields != null) {
			return;
		}
		Map<String, Field> theFields = new LinkedHashMap<>();
		Map<Field, ContextualType<?>> theFieldTypes = new LinkedHashMap<>();
		ClassContext<?> currentClass = this;
		do {
			Field[] declaredFields = currentClass.getActualClass().getDeclaredFields();
			try {
				AccessibleObject.setAccessible(declaredFields, true);
			} catch (InaccessibleObjectException | SecurityException e) {
			}
			for (Field field : declaredFields) {
				if (field.isSynthetic() || theFields.containsKey(field.getName())) {
					continue;
				}
				theFields.put(field.getName(), copyField(field));
				Type actualType = ClassUtil.getBound(TypeResolver.resolve(field.getGenericType(), currentClass.getContext()));
				theFieldTypes.put(field, ClassUtil.analyze(actualType));
			}
			currentClass = currentClass.getSuperclass();
		} while (currentClass != null);

		fields = Collections.unmodifiableMap(theFields);
		fieldTypes = Collections.unmodifiableMap(theFieldTypes);
	}

	Map<Field, ContextualType<?>> getFieldTypes() {
		computeFields();
		return fieldTypes;
	}

	public List<Field> getAllFields() {
		computeFields();
		return fields.values().stream().map(this::copyField).toList();
	}

	/**
	 * Get the Field with the given fieldName, searching up the superclasses of this ClassContext. A NoSuchFieldException is thrown if such field is not found.
	 */
	public Field getField(String fieldName) throws NoSuchFieldException {
		computeFields();
		Field field = fields.get(fieldName);
		if (field == null) {
			throw new NoSuchFieldException(fieldName);
		}
		return copyField(field);
	}

	/**
	 * Get the ContextualType of the field with the given fieldName, searching up the superclasses of this ClassContext. A NoSuchFieldException is thrown if such field is not found.
	 */
	public <R> ContextualType<R> getFieldType(String fieldName) throws NoSuchFieldException {
		computeFields();
		ContextualType<R> type = (ContextualType<R>) fieldTypes.get(fields.get(fieldName));
		if (type == null) {
			throw new NoSuchFieldException(fieldName);
		}
		return type;
	}

	private <S> ClassContext<S> toImplementation(Class<S> clazz) {
		return (ClassContext<S>) inferredImplementation.computeIfAbsent(clazz, c -> ClassContext.ofType(TypeResolver.newResolvedClassType(c, InferUtil.infer(c, this))));
	}

	protected T randomInstance(ValueGenerator generator, RandomInstanceState state) throws ReflectiveOperationException {
		computeFields();
		Class<?> implementationClass = generator.getImplementationClass(this, state.joinPath());
		if (implementationClass != null && implementationClass != actualClass) {
			return (T) toImplementation(implementationClass).randomInstance(generator, state);
		}

		CurrentInstanceContext instanceCreator = () -> {
			return generator.generate(
					this,
					state.joinPath(),
					() -> {
						Object instance = newInstance(outerClass == null || ClassUtil.isStaticClass(actualClass) ? null : outerClass.randomInstance(generator, state));
						state.pushInstance(this, instance);

						if (instance instanceof Collection<?> collection) {
							ClassContext<?> collectionFieldType = (ClassContext<?>) this;
							if (collectionFieldType.getActualClass() != Collection.class) {
								collectionFieldType = collectionFieldType.getInterface(Collection.class);
							}
							ContextualType<?> itemType = ClassUtil.analyze(collectionFieldType.getContext().get(Collection.class.getTypeParameters()[0])).intern();

							int size = generator.getCollectionSize(this, state.joinPath());
							for (int i = 0; i < size; i++) {
								state.pushIndexPath(i);
								((Collection<Object>) collection).add(itemType.randomInstance(generator, state));
								state.popPath();
							}
						}
						if (instance instanceof Map<?, ?> map) {
							ClassContext<?> mapFieldType = (ClassContext<?>) this;
							if (mapFieldType.getActualClass() != Map.class) {
								mapFieldType = mapFieldType.getInterface(Map.class);
							}
							Map<TypeVariable<?>, Type> mapTypeMap = mapFieldType.getContext();
							ContextualType<?> keyType = ClassUtil.analyze(mapTypeMap.get(Map.class.getTypeParameters()[0])).intern();
							ContextualType<?> valueType = ClassUtil.analyze(mapTypeMap.get(Map.class.getTypeParameters()[1])).intern();

							int size = generator.getCollectionSize(this, state.joinPath());;
							for (int i = 0; i < size; i++) {
								state.pushIndexPath(i);
								state.pushMapKeyPath();
								Object mapKeyInstance = keyType.randomInstance(generator, state);
								state.popPath();
								state.pushMapValuePath();
								Object mapValueInstance = valueType.randomInstance(generator, state);
								state.popPath();
								((Map<Object, Object>) map).put(mapKeyInstance, mapValueInstance);
								state.popPath();
							}
						}

						for (Map.Entry<Field, ContextualType<?>> entry : fieldTypes.entrySet()) {
							Field field = entry.getKey();
							if (Modifier.isStatic(field.getModifiers())) {
								continue;
							}
							if (generator.isIgnoredField(this, state.joinPath(), fields.get(field.getName()))) {
								continue;
							}

							ContextualType<?> fieldType = entry.getValue();
							state.pushFieldPath(field.getName());
							field.set(instance, fieldType.randomInstance(generator, state));
							state.popPath();
						}

						state.popInstance(this);
						return instance;
					});
		};
		List<Object> recursed = state.getInstances(this);
		Object instance;
		if (recursed.size() > 0) {
			instance = generator.onRecursion(this, state.joinPath(), recursed, instanceCreator);
		} else {
			instance = instanceCreator.randomInstance();
		}
		return (T) instance;
	}


	@Override
	public String toString() {
		return actualClass.getName() + " " + context;
	}
}
