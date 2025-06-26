package util.reflect;

import java.io.Serial;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

@SuppressWarnings("unchecked")
public class InferUtil {

	public static class TypeInferenceException extends RuntimeException {
		@Serial
		private static final long serialVersionUID = -4360690491679801300L;
		public TypeInferenceException() {
		}
		public TypeInferenceException(String message) {
			super(message);
		}
	}

	private enum Variance {
		INVARIANT, COVARIANT, CONTRAVARIANT
	}

	private static void infer(Map<TypeVariable<?>, Type> map, Type[] typesToInfer, Type[] actualTypes) {
		if (typesToInfer.length != actualTypes.length) {
			throw new TypeInferenceException();
		}
		for (int i = 0; i < typesToInfer.length; i++) {
			infer(map, typesToInfer[i], actualTypes[i], Variance.INVARIANT);
		}
	}

	private static void infer(Map<TypeVariable<?>, Type> map, Type typeToInfer, Type actualType, Variance variance) {
		if (typeToInfer == null && actualType == null) {
			return;
		}
		if (typeToInfer == null || actualType == null) {
			throw new TypeInferenceException();
		}

		Class<?> actualClass = ClassUtil.toBasicClass(actualType);
		if (typeToInfer instanceof WildcardType theTypeToInfer) {
			if (actualType instanceof WildcardType theActualType) {
				infer(map, theTypeToInfer.getLowerBounds(), theActualType.getLowerBounds());
				infer(map, theTypeToInfer.getUpperBounds(), theActualType.getUpperBounds());
			} else {
				// assume wildcard type can only have one bound, as per the current java language
				if (theTypeToInfer.getLowerBounds().length > 0) {
					infer(map, theTypeToInfer.getLowerBounds()[0], actualType, Variance.COVARIANT);;
				} else {
					infer(map, theTypeToInfer.getUpperBounds()[0], actualType, Variance.CONTRAVARIANT);;
				}
			}
			return;
		} else if (typeToInfer instanceof TypeVariable<?> theTypeToInfer) {
			if (variance != Variance.CONTRAVARIANT) {
				if (actualType instanceof Class<?> actual && actualClass.isPrimitive()) {
					actualClass = ClassUtil.box(actualClass);
					map.put((TypeVariable<Class<?>>) theTypeToInfer, actualClass);
				} else {
					map.put((TypeVariable<Class<?>>) theTypeToInfer, actualType);
				}
			}
			for (Type bound : theTypeToInfer.getBounds()) {
				Class<?> boundClass = ClassUtil.toBasicClass(bound);
				if (!boundClass.isAssignableFrom(actualClass)) {
					throw new TypeInferenceException();
				}
				if (bound instanceof ParameterizedType theBound) {
					try {
						Map<TypeVariable<?>, Type> boundClassMap = infer(boundClass, ClassContext.ofParameterizedType((ParameterizedType) actualType));
						ParameterizedType inferredBoundType = (ParameterizedType) TypeResolver.newResolvedClassType(boundClass, boundClassMap);
						infer(map, theBound.getActualTypeArguments(), inferredBoundType.getActualTypeArguments());
						infer(map, theBound.getOwnerType(), inferredBoundType.getOwnerType(), Variance.INVARIANT);
					} catch (ClassCastException e) {
						throw new TypeInferenceException();
					}
				}
				// TypeVariable bounds cannot be of array types or wild card types
				// TypeVariable bounds of Class and another TypeVariable will not give an inference info
			}
			return;
		} else if (typeToInfer instanceof ParameterizedType theTypeToInfer) {
			if (actualType instanceof ParameterizedType theActualType) {
				infer(map, theTypeToInfer.getRawType(), theActualType.getRawType(), variance);
				if (variance == Variance.INVARIANT) {
					infer(map, theTypeToInfer.getActualTypeArguments(), theActualType.getActualTypeArguments());
					infer(map, theTypeToInfer.getOwnerType(), theActualType.getOwnerType(), Variance.INVARIANT);
				} else {
					try {
						Class<?> rawClass = ClassUtil.toBasicClass(theTypeToInfer.getRawType());
						Map<TypeVariable<?>, Type> rawClassMap = infer(rawClass, ClassContext.ofParameterizedType(theActualType));
						ParameterizedType inferredRawType = (ParameterizedType) TypeResolver.newResolvedClassType(rawClass, rawClassMap);
						infer(map, theTypeToInfer.getActualTypeArguments(), inferredRawType.getActualTypeArguments());
						infer(map, theTypeToInfer.getOwnerType(), inferredRawType.getOwnerType(), Variance.INVARIANT);
					} catch (ClassCastException e) {
						throw new TypeInferenceException();
					}
				}
			} else if (actualType instanceof WildcardType theActualType) {
				// assume wildcard type can only have one bound, as per the current java language
				if (theActualType.getLowerBounds().length > 0) {
					infer(map, theTypeToInfer, theActualType.getLowerBounds()[0], Variance.CONTRAVARIANT);
				} else {
					infer(map, theTypeToInfer, theActualType.getUpperBounds()[0], Variance.COVARIANT);
				}
			} else {
				throw new TypeInferenceException();
			}
			return;
		} else if (typeToInfer instanceof GenericArrayType theTypeToInfer) {
			if (actualType instanceof GenericArrayType theActualType) {
				infer(map, theTypeToInfer.getGenericComponentType(), theActualType.getGenericComponentType(), Variance.COVARIANT);
			} else if (actualType instanceof Class<?> theActualType) {
				infer(map, theTypeToInfer.getGenericComponentType(), theActualType.getComponentType(), Variance.COVARIANT);
			} else if (actualType instanceof WildcardType theActualType) {
				if (theActualType.getLowerBounds().length > 0) {
					infer(map, theTypeToInfer, theActualType.getLowerBounds()[0], Variance.CONTRAVARIANT);
				} else {
					infer(map, theTypeToInfer, theActualType.getUpperBounds()[0], Variance.COVARIANT);
				}
			} else {
				throw new TypeInferenceException();
			}
			return;
		}

		if (variance == Variance.INVARIANT && !typeToInfer.equals(actualType)
			|| variance == Variance.COVARIANT && !actualClass.isAssignableFrom(ClassUtil.toBasicClass(typeToInfer))
			|| variance == Variance.CONTRAVARIANT && !ClassUtil.toBasicClass(typeToInfer).isAssignableFrom(actualClass)) {
			throw new TypeInferenceException();
		}
	}

	private static <S, T> Map<TypeVariable<?>, Type> inferDirectSubclass(Class<T> targetClass, ClassContext<S> declaredClassContext) {
		Class<S> declaredClass = declaredClassContext.getActualClass();
		Map<TypeVariable<?>, Type> inferredTypes = new HashMap<>();
		TypeVariable<Class<T>>[] targetClassTypeParams = targetClass.getTypeParameters();
		Type superType = declaredClass.isInterface()
				? Stream.of(targetClass.getGenericInterfaces()).filter(type -> ClassUtil.toBasicClass(type) == declaredClass).findFirst().get()
				: targetClass.getGenericSuperclass();

		// This inference call makes sure the class declaration fulfills the given declaration
		infer(new HashMap<>(), superType, TypeResolver.newResolvedClassType(declaredClass, declaredClassContext.getContext()), Variance.INVARIANT);

		if (targetClassTypeParams.length > 0 && superType instanceof ParameterizedType theSuperType) {
			Type[] superTypeTypeArguments = theSuperType.getActualTypeArguments();
			TypeVariable<Class<S>>[] declaredClassTypeParams = declaredClass.getTypeParameters();
			for (int i = 0; i < superTypeTypeArguments.length; i++) {
				infer(inferredTypes, superTypeTypeArguments[i], declaredClassContext.getContext().getOrDefault(declaredClassTypeParams[i], declaredClassTypeParams[i]), Variance.INVARIANT);
			}
		}

		if (!ClassUtil.isStaticClass(targetClass)) {
			Class<?> outerClass = ClassUtil.isInnerClass(targetClass) ? targetClass.getEnclosingClass() : null;
			ClassContext<?> declaredOuterClassContext = declaredClassContext.getOuterClass();
			while (outerClass != null && declaredOuterClassContext != null) {
				if (declaredOuterClassContext.getActualClass().isAssignableFrom(outerClass)) {
					inferredTypes.putAll(infer(outerClass, declaredOuterClassContext));
					declaredOuterClassContext = declaredOuterClassContext.getOuterClass();
				}
				outerClass = outerClass.getEnclosingClass();
			}
		}
		return inferredTypes;
	}

	/**
	 * Given the ClassContext, infer TypeVariables of the targetClass, which may be a subclass, a sub-interface, a superclass, or a super-interface.
	 *
	 * The main use of this is to infer an implementation class for a ClassContext representing an interface when generating random instances.
	 * The inferred types will be correct and meaningful for general use cases.
	 * Some invalid declaration will also be caught and a TypeInferenceException will be thrown but it is not exhaustive and should not be used to verify the declaration.
	 */
	public static <S, T> Map<TypeVariable<?>, Type> infer(Class<T> targetClass, ClassContext<S> declaredClassContext) throws TypeInferenceException {
		Class<S> declaredClass = declaredClassContext.getActualClass();
		if (targetClass == declaredClass) {
			return declaredClassContext.getContext();
		}
		if (targetClass.isAssignableFrom(declaredClass)) {
			if (targetClass.isInterface()) {
				return declaredClassContext.getInterface(targetClass).getContext();
			} else {
				return infer(targetClass, declaredClassContext.getSuperclass());
			}
		}
		if (declaredClass.isAssignableFrom(targetClass)) {
			if (targetClass.isInterface()) {
				for (Class<?> superInterface : targetClass.getInterfaces()) {
					if (superInterface == declaredClass) {
						return inferDirectSubclass(targetClass, declaredClassContext);
					} else if (declaredClass.isAssignableFrom(superInterface)) {
						ClassContext<?> superclassContext = ClassContext.ofType(TypeResolver.newResolvedClassType(superInterface, infer(superInterface, declaredClassContext)));
						return infer(targetClass, superclassContext);
					}
					throw new TypeInferenceException();
				}
			} else {
				if (declaredClass.isInterface()) {
					for (Class<?> superInterface : targetClass.getInterfaces()) {
						if (superInterface == declaredClass) {
							return inferDirectSubclass(targetClass, declaredClassContext);
						} else if (declaredClass.isAssignableFrom(superInterface)) {
							ClassContext<?> superclassContext = ClassContext.ofType(TypeResolver.newResolvedClassType(superInterface, infer(superInterface, declaredClassContext)));
							return infer(targetClass, superclassContext);
						}
					}
					Class<?> superclass = targetClass.getSuperclass();
					ClassContext<?> superclassContext = ClassContext.ofType(TypeResolver.newResolvedClassType(superclass, infer(superclass, declaredClassContext)));
					return infer(targetClass, superclassContext);
				} else {
					Class<?> superclass = targetClass.getSuperclass();
					if (superclass == declaredClass) {
						return inferDirectSubclass(targetClass, declaredClassContext);
					} else {
						ClassContext<?> superclassContext = ClassContext.ofType(TypeResolver.newResolvedClassType(superclass, infer(superclass, declaredClassContext)));
						return infer(targetClass, superclassContext);
					}
				}
			}
		}
		throw new TypeInferenceException("targetClass is not on the type chain of declaredClassContext");
	}
}
