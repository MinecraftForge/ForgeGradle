#! /usr/bin/env groovy

// check file
File jsonFile;

if (args.length > 0)
{
    jsonFile = new File(args[0])
    if (!jsonFile.exists())
    {
        println "JSON file $jsonFile not found!"
        return 1
    }
}
else
{   
    jsonFile = new File("ForgeGradleVersion.json")
    if (!jsonFile.exists())
    {
        println "json file (forgegradleversion.json) not found!"
        return 1
    }
}

println "analyzing file: $jsonFile"

// start verifying
import groovy.json.*

def result = new JsonSlurper().parse(jsonFile);
boolean broke = false;
boolean warned = false;

// check if list is sorted
def sorted = result.versionNumbers.sort(false) // false so it returns a new list instead of editting

if (sorted != result.versionNumbers)
{
    broke = true
    println "ERROR --"
    println "Version numbers are not sorted. Should be.. "
    println new JsonBuilder(sorted).toPrettyString()
    println " -- "
}

if (sorted.size() > result.versionObjects.size())
{
    broke = true
    println "ERROR -- More versions defined in objects than there are in the list"
}
else if (sorted.size() < result.versionObjects.size())
{
    broke = true
    println "ERROR -- Fewer versions defined in objects than there are in the list"
}

// start valterating over objects
result.versionObjects.each { key, val ->

    if (key != val.version)
    {
        broke = true
        println "ERROR -- Object Key and value version dont match"
    }

    // check if indexes match
    if (key != sorted[val.index])
    {
        broke = true
        println "ERROR -- Index of '$key' (${sorted[val.index]}) doesnt match vals actual index in the sorted list. Should be ${sorted.indexOf key}"
    }

    // check outdated status
    if (val.outdated)
    {
        // verify list posvalion
        if (key == sorted.last())
        {
            broke = true
            println "ERROR -- '$key' is marked 'outdated' even though vals the newest version"
        }
    }
    else // not outdated obviously
    {
        // verify list posvalion
        if (key != sorted.last())
        {
            broke = true
            println "ERROR -- '$key' is not marked 'outdated' even though there is a newer version"
        }

        // check for changes
        if (val.changes.size() <= 0)
        {
            warned = true
            println "WARNING -- '$key' is the newest version, but has no defined changes"
        }

        // check broken state
        if (val.broken)
        {
            broke = true
            println "ERROR -- '$key' is marked 'broken' even though val is not outdated"
        }
    }

    if (val.broken)
    {
        // check for bugs
        if (val.bugs.size() <= 0)
        {
            warned = true
            println "WARNING -- '$key' is marked as 'broken', yet has no bugs defined"
        }
    }

    if (!val.docUrl)
    {
        warned = true
        println "WARNING -- '$key' doesnt have a 'docUrl' defined!"
    }
    else if (!val.docUrl.startsWith('http://') && !val.docUrl.startsWith('https://'))
    {
        broke = true
        println "WARNING -- '$key' has a 'docUrl' that isnt an HTTP URL"
    }
}

// ----------
// END AND RETURN
// ----------
if (broke)
{
    println()
    println "JSON has errors   :("
    return 1
}
else if (warned)
{
    println()
    println "JSON has warnings   :|"
    return 1
}
else
{   
    println()
    println "JSON is perfect   :D"
    return 0
}
