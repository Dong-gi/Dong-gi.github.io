package jackson;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.Executors;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.Data;

public class JsonTest {
    @Data
    public static class Data1 {
        public int id;
        public String name;
        public Data1() {
            id = RandomUtils.nextInt();
            name = RandomStringUtils.randomGraph(32);
        }
    }
    @Test
    public void pojoTest1() throws JsonGenerationException, JsonMappingException, IOException {
        var data = new Data1();
        var mapper = new ObjectMapper();
        var json = mapper.writeValueAsString(data);
        System.out.println(json);
        assertTrue(data.equals(mapper.readValue(json, Data1.class)));
        System.out.println(mapper.readValue("{}", Data1.class)); // JsonTest.Data1(id=1711995016, name=f{|\(Dlh0fdfpXkOQnyE&PuHK5K7:NOw)
    }


    @Data
    public static class Data2 {
        public int id;
        private String name;
        public Data2() {
            id = RandomUtils.nextInt();
            name = RandomStringUtils.randomGraph(32);
        }
    }
    @Test
    public void pojoTest2() throws JsonGenerationException, JsonMappingException, IOException {
        var data = new Data2();
        var mapper = new ObjectMapper();
        var json = mapper.writeValueAsString(data);
        System.out.println(json);
        assertTrue(data.equals(mapper.readValue(json, Data2.class)));
    }

    @Test
    public void listTest() throws JsonGenerationException, JsonMappingException, IOException {
        var list = Arrays.asList(
                Arrays.asList(new Data1(), new Data1()),
                Arrays.asList(new Data1(), new Data1())
                );
        new ObjectMapper().writeValue(System.out, list);
    }

    @Test
    public void nodeTest() throws JsonGenerationException, JsonMappingException, IOException {
        var data = new Data1();
        var mapper = new ObjectMapper();
        var writer = new StringWriter();

        mapper.writeValue(writer, data);
        var json = writer.toString();
        ObjectNode root = ObjectNode.class.cast(mapper.readTree(json));
        root.put("extended", true);
        root.with("moreData").set("arr", mapper.createArrayNode().add(1).add(2).add(3));
        mapper.writeValue(System.out, root);
    }

    @Test
    public void generatorTest() throws IOException {
        var mapper = new ObjectMapper();
        var factory = mapper.getFactory();
        var writer = new StringWriter();
        try(var generator = factory.createGenerator(writer)) {
            generator.writeStartObject();
            generator.writeFieldName("id");
            generator.writeNumber(12345);
            generator.writeStringField("name", null);
            generator.writeEndObject();
        }
        System.out.println(writer);
    }

    @Test
    public void parserTest() throws JsonParseException, IOException {
        var json = "{\"id\":628016310,\"name\":\"FBwfi_&ezLbxupF2EZYftTOYGa_/NYan\",\"extended\":true,\"moreData\":{\"arr\":[1,2,3]}}";
        var mapper = new ObjectMapper();
        var factory = mapper.getFactory();
        try(var parser = factory.createParser(json)) {
            while(parser.nextToken() != null)
                System.out.printf("token : %s, name : %s, value : %s\n", parser.getCurrentToken(), parser.getCurrentName(), parser.getValueAsString());
        }
    }

    @Test
    public void optionTest() throws JsonProcessingException {
        var mapper = new ObjectMapper();

        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        System.out.println(mapper.writeValueAsString(new Object() {})); // {}

        System.out.println(mapper.writeValueAsString(new Date())); // 1574154313945
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        System.out.println(mapper.writeValueAsString(new Date())); // "2019-11-19T09:05:54.761+0000"

        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        System.out.println(mapper.readValue("{\"id\":123,\"name\":null,\"nickname\":null}", Data1.class)); // JsonTest.Data1(id=123, name=null)

        mapper.enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES);
        System.out.println(mapper.readValue("{id:123,name:null}", Data1.class)); // JsonTest.Data1(id=123, name=null)

        mapper.enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES);
        var nonAsciiData = mapper.readValue("{'id':'123','name':'안녕 세상'}", Data1.class);
        System.out.println(nonAsciiData); // JsonTest.Data1(id=123, name=hello world)

        System.out.println(mapper.writeValueAsString(nonAsciiData)); // {"id":123,"name":"안녕 세상"}
        System.out.println(JsonMapper.builder() // {"id":123,"name":"\uC548\uB155 \uC138\uC0C1"}
                .configure(JsonWriteFeature.ESCAPE_NON_ASCII, true)
                .build()
                .writeValueAsString(nonAsciiData));
    }

    @Test
    public void dateTest() throws InterruptedException {
        var mapper = new ObjectMapper();
        mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
        var pool = Executors.newCachedThreadPool();
        for(var i = 0; i < 1000; ++i) {
            pool.execute(() -> {
                try {
                    System.out.println(mapper.writeValueAsString(new Date()));
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }
            });
        }
        Thread.sleep(1000);
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    static class Data3 extends Data2 {}

    @Test
    public void includeTest() throws JsonProcessingException {
        var mapper = new ObjectMapper();
        var data = new Data3();
        System.out.println(mapper.writeValueAsString(data));
        data.setName("");
        System.out.println(mapper.writeValueAsString(data));
    }
}
