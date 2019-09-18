package io.github.donggi.bean;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import lombok.Data;

@Data
public class Hello5 {
    private String[] messageArr;
    private List<String> messageList;
    private Set<String> messageSet;
    private Map<String, String> messageMap;
    private Properties messageProps;
}
