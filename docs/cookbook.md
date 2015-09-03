#The CookBook
This page houses a collection of recipes. In this sense a Recipe is specific exact instructions to accomplish a given task. The majority of these recipes are simply snippets of Gradle scripts with minimal explanation needed for an absolute beginner to use.

###Creating a deobfuscated jar
```
task deobfJar(type: Jar, dependsOn: 'jar') {
    from "build/source/main"
    classifier "dev"
}
artifacts {
    archives deobfJar
}
```

###Creating a source jar
```
task sourceJar(type: Jar, dependsOn: 'sourceMainJava') {
    from "build/sources/java"
    from "build/resources/main/java"
    classifier "sources"
}
artifacts {
    archives sourceJar
}
```

###Creating a javadoc jar
```
task javadocJar(type: Jar, dependsOn: 'javadoc') {
    from "build/docs/javadoc"
    classifier "javadoc"
}
artifacts {
    archives javadocJar
}
```

###Using environment variables
In this case, BUILD_NUMBER is the environment variable we care about.
```
version = "1.2.3."+System.env.BUILD_NUMBER
```

### Getting a git hash
Not everyone has git installed and on their PATH, so it is usually best to only do this if the environment variable is missing or something. In this example the BUILD_NUMBER environment variable is checked first before trying the git command, and if the git command fails for any reason the string "GITBROK" is used.
```
def getVersionAppendage() {
    if (System.env.BUILD_NUMBER)
        return System.env.BUILD_NUMBER

    def proc = "git rev-parse --short HEAD".execute()
    proc.waitFor()
    return "DEV." + proc.exitValue() ? "GITBORK" : proc.text.trim()
}

version = "1.2.3_"+getVersionAppendage()
```

### Shading
In this example, the EJML library is being shaded. This can be done with almost any jar you can define as a dependency. Each library has a different package its files are in, in this case EJMLs root package is ```org.ejml```, and I want to relocate it to ```your.new.package.here.ejml```. Notice that the periods have been replaced with slashes.
```
minecraft {
    srgExtra "PK: org/ejml your/new/package/here/ejml"
}

configurations {
    shade
    compile.extendsFrom shade
}

dependencies {
    shade 'com.googlecode.efficient-java-matrix-library:ejml:0.25'
}

jar {
    configurations.shade.each { dep ->
        from(project.zipTree(dep)){
            exclude 'META-INF', 'META-INF/**'
            // you may exclude other things here if you want, or maybe copy the META-INF
        }
    }
}
```
