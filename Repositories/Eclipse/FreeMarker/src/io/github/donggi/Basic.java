package io.github.donggi;

import java.io.*;
import java.util.*;

import org.junit.*;
import org.junit.jupiter.api.*;

import freemarker.template.*;




@SuppressWarnings({"unused", "serial"})
public class Basic {	// 15 라인 고정
	// 예제 영역
	public void example_t_001() throws Exception {
		var path = "t_001_hello_world.ftl";
		process(path, new HashMap<String, Object>() {{
			put("title", path);
			put("name", "Donggi");
		}});
	}

	public void example_t_002() throws Exception {
		var path = "t_002_hello.ftl";
		process(path, new HashMap<String, Object>() {{
			put("title", path);
		}});
	}

	public void example_t_003() throws Exception {
		var path = "t_003_escape.ftl";
		process(path, new HashMap<String, Object>() {{
			put("title", path);
			put("text", "<h5>hello</h5>");
		}});
	}

	public void example_t_004() throws Exception {
		var path = "t_004_type.ftl";
		process(path, new HashMap<String, Object>() {{
			put("title", path);
			put("date", new Date());
		}});
	}

	public void example_t_005() throws Exception {
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

	public void example_t_006() throws Exception {
		var path = "t_006_syntax.ftl";
		process(path, new HashMap<String, Object>() {{
			put("title", path);
		}});
	}

	public void example_t_007() throws Exception {
		var path = "t_007_macro.ftl";
		process(path, new HashMap<String, Object>() {{
			put("title", path);
		}});
	}

	public void example_t_008() throws Exception {
		var path = "t_008_macro.ftl";
		process(path, new HashMap<String, Object>() {{
			put("title", path);
		}});
	}

	public void example_t_009() throws Exception {
		var path = "t_009_macro.ftl";
		process(path, new HashMap<String, Object>() {{
			put("title", path);
		}});
	}

	public void example_t_010() throws Exception {
		var path = "t_010_macro.ftl";
		process(path, new HashMap<String, Object>() {{
			put("title", path);
		}});
	}

	public void example_t_011() throws Exception {
		var path = "t_011_main.ftl";
		process(path, new HashMap<String, Object>() {{
			put("title", path);
		}});
	}
	@org.junit.jupiter.api.Test
	public void example_t_012() throws Exception {
		config.addAutoImport("footer", "t_012_footer.ftl"); 
		config.setLazyAutoImports(true);
		var path = "t_012_main.ftl";
		process(path, new HashMap<String, Object>() {{
			put("title", path);
		}});
	}
	// 끝. 그만 추가.

	public static Configuration config;
	public static File templateRootFile;

	@BeforeAll
	public static void config() throws Exception {
		config = new Configuration(Configuration.VERSION_2_3_28);
		templateRootFile = new File(System.getProperty("user.dir"), "resource/template");
		config.setDirectoryForTemplateLoading(templateRootFile);
		config.setDefaultEncoding("UTF-8");
		config.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
		config.setWrapUncheckedExceptions(true);
	}

	public static void process(String path, Object map) throws Exception {
		var template = config.getTemplate(path);
		var writer = new StringWriter();
		try {
			template.process(map, writer);
		} catch(Exception e) {
			e.printStackTrace(new PrintWriter(writer));
		}
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
