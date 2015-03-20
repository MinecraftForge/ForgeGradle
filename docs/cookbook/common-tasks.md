This page houses a collection of commonly requested snippets of configuration used with ForgeGradle. Some of them are easy and can easily be copied to your buildscript, but most will require that you replace some token with something from your own project.

###Creating a dev jar
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
Not everyone has git installed and on their PATH, so it is usually best to only do this if the environment variable is missing or something. In this example the BUILD_NUMBER environment variable is checked first before trying the git command, and if the git command fails for any reasn the string "GITBROK" is used.
```
def getVersionAppendage() {
    if (System.env.BUILD_NUMBER)
        return System.env.BUILD_NUMBER
    
    def proc = "git rev-parse --short HEAD".execute()
    proc.waitFor()
    return "DEV." + proc.exitValue() ? prox.text.trim() : "GITBORK"
}

version = "1.2.3_"+getVersionAppendage()
```
