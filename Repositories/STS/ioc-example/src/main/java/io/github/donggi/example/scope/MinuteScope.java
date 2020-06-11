package io.github.donggi.example.scope;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;

public class MinuteScope implements Scope {

    private Map<String, ImmutablePair<Long, Object>> map = new HashMap<>();
    
    @Override
    public Object get(String name, ObjectFactory<?> objectFactory) {
        var r = map.get(name);
        if (r == null || r.left != getMinute()) {
            r = new ImmutablePair<Long, Object>(getMinute(), objectFactory.getObject());
            map.put(name, r);
        }
        return r.right;
    }

    @Override
    public Object remove(String name) {
        if (!map.containsKey(name))
            return null;
        return map.remove(name);
    }

    @Override
    public void registerDestructionCallback(String name, Runnable callback) {
        throw new UnsupportedOperationException(); 
    }

    @Override
    public Object resolveContextualObject(String key) {
        return null;
    }

    @Override
    public String getConversationId() {
        return String.valueOf(getMinute());
    }

    private long getMinute() {
        return System.currentTimeMillis() / 60000;
    }
}
