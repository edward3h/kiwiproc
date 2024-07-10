plugins {
    id("java-convention")
}

dependencies {
    api(project(":shared"))
    api(libs.mapstruct.compile)
}
