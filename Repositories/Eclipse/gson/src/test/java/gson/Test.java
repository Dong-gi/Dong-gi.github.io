package gson;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.Executors;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.Expose;
import com.google.gson.stream.JsonWriter;

import lombok.Data;

public class Test {
    @Data
    public static class Data1 {
        public int id;
        public String name;
        public Data1() {
            id = RandomUtils.nextInt();
            name = RandomStringUtils.randomGraph(32);
        }
    }

    public void pojoTest1() {
        var data = new Data1();
        var gson = new Gson();
        var json = gson.toJson(data);
        System.out.println(json);
        assertTrue(data.equals(gson.fromJson(json, Data1.class)));
        System.out.println(gson.fromJson("{}", Data1.class)); // Test.Data1(id=315478902, name=DA`\oJcbn<tVBE[zF8\^=8;`wxgm699U)
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

    public void pojoTest2() {
        var data = new Data2();
        var gson = new Gson();
        var json = gson.toJson(data);
        System.out.println(json);
        assertTrue(data.equals(gson.fromJson(json, Data2.class)));
    }


    public void listTest() {
        var list = Arrays.asList(
                Arrays.asList(new Data1(), new Data1()),
                Arrays.asList(new Data1(), new Data1())
                );
        System.out.println(new Gson().toJson(list));
    }


    public void nodeTest() {
        var data = new Data1();
        var gson = new Gson();

        var root = JsonParser.parseString(gson.toJson(data)).getAsJsonObject();
        root.addProperty("extended", true);

        var arr = new JsonArray();
        for(var num = 0; num < 5; ++num)
            arr.add(num);
        var newProperty = new JsonObject();
        newProperty.add("arr", arr);
        root.add("moreData", newProperty);

        System.out.println(gson.toJson(root));
        // {"id":2116601366,"name":"[P[)\\;RC-(\u003e/q6daw:u4NThjs\u0027G5Am%E","extended":true,"moreData":{"arr":[0,1,2,3,4]}}
    }


    public void writerTest() throws IOException {
        var strWriter = new StringWriter();
        try(var writer = new JsonWriter(strWriter)) {
            writer.beginObject();
            writer.name("id").value(12345);
            writer.name("name").value((String)null);
            writer.endObject();
        }
        System.out.println(strWriter); // {"id":12345,"name":null}
    }


    public void parserTest() {
        var json = "{\"id\":628016310,\"name\":\"FBwfi_&ezLbxupF2EZYftTOYGa_/NYan\",\"extended\":true,\"moreData\":{\"arr\":[1,2,3]}}";
        var obj = JsonParser.parseString(json).getAsJsonObject();
        for(var entry : obj.entrySet())
            System.out.printf("entry : %d, name : %s, value : %s\n", entry.hashCode(), entry.getKey(), entry.getValue());
    }

    public static class Data3 extends Data2 {
        @Expose public int something = 0;
    }

    @org.junit.Test
    public void optionTest() {
        var gson = new Gson();

        var newGson = new GsonBuilder().excludeFieldsWithModifiers(Modifier.PRIVATE).create();
        var data = new Data2();
        System.out.println(gson.toJson(data)); // {"id":1055887029,"name":"}$zM/1f)}3|A9r%sc*b:g\u003dV4}#}?^SM^"}
        System.out.println(newGson.toJson(data)); // {"id":1055887029}

        data = new Data3();
        newGson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
        System.out.println(gson.toJson(data)); // {"something":0,"id":1232551903,"name":"*m|xN*Ey5vnOB\\klN0h0emNV\u0026*\u003ef4C\u0026Z"}
        System.out.println(newGson.toJson(data)); // {"something":0}

        data.name = null;
        newGson = new GsonBuilder().serializeNulls().create();
        System.out.println(gson.toJson(data)); // {"something":0,"id":193973836}
        System.out.println(newGson.toJson(data)); // {"something":0,"id":193973836,"name":null}
    }

    @org.junit.Test
    public void dateTest() throws InterruptedException {
        var gson = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
        var pool = Executors.newCachedThreadPool();
        for(var i = 0; i < 1000; ++i) {
            pool.execute(() -> {
                System.out.println(gson.toJson(new Date()));
            });
        }
        Thread.sleep(1000);
    }

}
