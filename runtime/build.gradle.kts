plugins {
    id("java-convention")
    id("publishing-convention")
}

dependencies {
    api(project(":shared"))
    api(libs.mapstruct.compile)
    testImplementation(libs.h2)
}
