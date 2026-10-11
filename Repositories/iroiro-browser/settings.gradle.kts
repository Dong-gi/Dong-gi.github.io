pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // 모듈이 제 리포지터리를 몰래 더하지 못하게 막는다. 의존성이 어디서 오는지가 이 파일 하나로 끝나야 공급망을 볼 수 있다.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "iroiro-browser"

include(":app")
include(":net")
include(":core")
include(":torrent")
include(":tether")
