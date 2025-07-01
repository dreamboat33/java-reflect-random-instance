# Java Reflect Random Instance

## Generate Java POJO with random values assigned to its fields.

Usage:
```
ClassContext<MyPojo> myPojoClassContext = ClassUtil.analyze(MyPojo.class);
MyPojo myPojo = myPojoClassContext.randomInstance();
```

Usage with generic types:
```
ClassContext<MyClass<Integer, String>> myClassContext = ClassUtil.analyze(new TypeReference<MyClass<Integer, String>>() {});
MyClass<Integer, String> myClass = myClassContext.randomInstance();
```

## Shallow clone Java POJO.

Usage:
```
MyPojo src = new MyPojo(...);
MyPojo clone = ClassUtil.shallowClone(src);
```

## Deep clone Java POJO.

Usage:
```
MyPojo src = new MyPojo(...);
MyPojo clone = ClassUtil.deepClone(src);
```

