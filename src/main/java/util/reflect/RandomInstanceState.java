package util.reflect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class RandomInstanceState {

	private List<String> path = new ArrayList<>();

	private String joinedPath;

	private Map<ContextualType<?>, List<Object>> recursiveReferences = new LinkedHashMap<>();

	void pushFieldPath(String name) {
		path.add(path.size() == 0 ? name : "." + name);
		joinedPath = null;
	}

	void pushIndexPath(int index) {
		path.add("[" + index + "]");
		joinedPath = null;
	}

	void pushMapKeyPath() {
		path.add("[:key]");
		joinedPath = null;
	}

	void pushMapValuePath() {
		path.add("[:value]");
		joinedPath = null;
	}

	void popPath() {
		path.remove(path.size() - 1);
		joinedPath = null;
	}

	String joinPath() {
		if (joinedPath == null) {
			joinedPath = String.join("", path);
		}
		return joinedPath;
	}

	void pushInstance(ContextualType<?> type, Object instance) {
		recursiveReferences.computeIfAbsent(type, t -> new ArrayList<>()).add(instance);
	}

	void popInstance(ContextualType<?> type) {
		List<Object> instances = recursiveReferences.get(type);
		instances.remove(instances.size() - 1);
	}

	List<Object> getInstances(ContextualType<?> type) {
		return recursiveReferences.getOrDefault(type, Collections.emptyList());
	}
}
