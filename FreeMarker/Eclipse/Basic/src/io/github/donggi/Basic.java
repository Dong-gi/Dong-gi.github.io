package io.github.donggi;

import java.io.*;
import java.util.*;

import freemarker.template.*;







@SuppressWarnings({"unused", "serial"})
public class Basic {	// 15 라인 고정
	// 예제 영역
	private static void example_t_001() throws Exception {
		var path = "t_001_hello_world.ftl";
		process(path, new HashMap<String, Object>() {{
			put("title", path);
			put("name", "Donggi");
		}});
	}

	private static void example_t_002() throws Exception {
		var path = "t_002_hello.ftl";
		process(path, new HashMap<String, Object>() {{
			put("title", path);
		}});
	}

	private static void example_t_003() throws Exception {
		var path = "t_003_escape.ftl";
		process(path, new HashMap<String, Object>() {{
			put("title", path);
			put("text", "<h5>hello</h5>");
		}});
	}

	private static void example_t_004() throws Exception {
		var path = "t_004_type.ftl";
		process(path, new HashMap<String, Object>() {{
			put("title", path);
			put("date", new Date());
		}});
	}

	private static void example_t_005() throws Exception {
		var path = "t_005_type.ftl";
		process(path, new HashMap<String, Object>() {{
			put("title", path);
			put("hash", new HashMap<String, Object>() {{
				put("num", 123.456);
				put("str", "hello");
			}});
			put("sequence", new Object[] {1, 3.14, "world"});
		}});
	}
	
	private static void example_t_006() throws Exception {
		var path = "t_006_syntax.ftl";
		process(path, new HashMap<String, Object>() {{
			put("title", path);
			put("hash", new HashMap<String, Object>() {{
				put("num", 123.456);
				put("str", "hello");
			}});
			put("sequence", new Object[] {1, 3.14, "world"});
		}});
	}
	
	
	public static Configuration config;
	public static File templateRootFile;

	public static void main(String[] args) throws Exception {
		config = new Configuration(Configuration.VERSION_2_3_28);
		templateRootFile = new File(System.getProperty("user.dir"), "resource/template");
		config.setDirectoryForTemplateLoading(templateRootFile);
		config.setDefaultEncoding("UTF-8");
		config.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
		config.setWrapUncheckedExceptions(true);

		example_t_006();
	}

	private static void process(String path, Object map) throws Exception {
		var template = config.getTemplate(path);
		var writer = new StringWriter();
		template.process(map, writer);
		System.out.print(writer.getBuffer());
		try(var fileWriter = new FileWriter(
				new File(templateRootFile, template.getName()),
				java.nio.charset.StandardCharsets.UTF_8,
				true)) {
			fileWriter.write("\n<#--\n");
			fileWriter.write(writer.getBuffer().toString());
			fileWriter.write("\n-->\n");
		}
	}

}
