package test.hello

import org.gradle.api.Plugin
import org.gradle.api.Project

class HelloPlugin implements Plugin<Project> {
    void apply(Project project) {
        def extension = project.extensions.create("hello", HelloExtension) // 외부 노출
        project.task("hello") {
            doLast {
                println "${extension.msg}, ${extension.name}!"
            }
        }
    }
}

class HelloExtension {
    String msg
    String name
    
    void message(msg, name) {
        this.msg = msg
        this.name = name
    }
}