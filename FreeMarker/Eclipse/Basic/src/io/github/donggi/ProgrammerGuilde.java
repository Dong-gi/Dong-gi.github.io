package io.github.donggi;

import java.io.*;
import java.util.*;
import java.util.stream.*;

import org.junit.*;
import org.junit.jupiter.api.*;

import freemarker.template.*;



@SuppressWarnings({"unused", "serial"})
public class ProgrammerGuilde {	// 15 라인 고정
	// 예제 영역
	
	public void example_t_013() throws Exception {
		var path = "t_013_method.ftl";
		process(path, new HashMap<String, Object>() {{
			put("title", path);
			put("printSomething", (TemplateMethodModelEx)(args) -> {
				return args.stream().map(x -> String.valueOf(x)).collect(Collectors.joining(", "));
			});
		}});
	}
	
	public void example_t_014() throws Exception {
		config.setWhitespaceStripping(true);
		var path = "t_014.ftl";
		process(path, new HashMap<String, Object>() {{
			put("title", path);
			put("root", freemarker.ext.dom.NodeModel.parse(new File(System.getProperty("user.dir"), "resource/template/t_014.xml")));
		}});
	}

	public void example_t_015() throws Exception {
		var path = "t_015.ftl";
		process(path, new HashMap<String, Object>() {{
			put("title", path);
		}});
	}

	public void example_t_016() throws Exception {
		var path = "t_016.html";
		process(path, new HashMap<String, Object>() {{
			put("title", path);
		}});
	}

	public void example_t_017() throws Exception {
		var path = "t_017.html";
		process(path, new HashMap<String, Object>() {{
			put("title", path);
		}});
	}

	public void example_t_018() throws Exception {
		var path = "t_018.html";
		process(path, new HashMap<String, Object>() {{
			put("title", path);
		}});
	}
	@org.junit.jupiter.api.Test
	public void example_t_019() throws Exception {
		var path = "t_019.html";
		process(path, new HashMap<String, Object>() {{
			put("title", path);
		}});
	}
	
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
