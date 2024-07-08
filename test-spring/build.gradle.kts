plugins {
    id("java-convention")
}

dependencies {
    annotationProcessor(project(":processor"))
    implementation(project(":runtime"))

}