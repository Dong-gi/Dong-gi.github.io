pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // 모듈이 제 리포지터리를 몰래 더하지 못하게 막는다. 의존성이 어디서 오는지가
    // 이 파일 하나로 끝나야 공급망을 볼 수 있다.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "iroiro-viewer"

include(":app")
include(":core:model")
include(":core:safety")
include(":core:charset")
include(":core:io")
include(":core:data")
include(":core:ui")
include(":core:playback")
include(":core:webhost")
include(":format:api")
include(":format:archive")
include(":format:html")
include(":format:epub")
include(":format:cfb")
include(":format:opc")
include(":format:docx")
include(":format:xlsx")
include(":format:pptx")
include(":format:hwpx")
include(":format:hwp5")
include(":format:text")
include(":feature:diag")
include(":feature:browser")
include(":feature:image")
include(":feature:text")
include(":feature:archive")
include(":feature:comic")
include(":feature:player")
include(":feature:docview")
include(":feature:settings")
