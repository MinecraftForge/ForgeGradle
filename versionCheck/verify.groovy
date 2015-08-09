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
println "----"
println ""

// start verifying
import groovy.json.*

def result = new JsonSlurper().parse(jsonFile);
broke = false;
warned = false;

// some convenience methods
public void error(msg)
{
    broke = true
    println "ERROR -- "+msg
}
public void warn(msg)
{
    warned = true
    println "WARNING -- "+msg
}

// check if list is sorted
def sorted = result.sort(false) { it.version } // false so it returns a new list instead of editting

if (sorted != result)
{
    println "ERROR --"
    println "Versions are not sorted. Should be.. "
    println new JsonBuilder(sorted).toPrettyString()
    println " -- "
}

// start valterating over objects
result.eachWithIndex { version, index ->

    def fieldBroke = false;
    // check mandatory field existances
    ['status', 'docUrl', 'version', 'changes', 'bugs'].each { field ->
        if (result["${field}"] == null)
        {
            fieldBroke = true
            error "Version object with index ${index} is missing field ${field}"
        }
    }
    
    if (fieldBroke)
    {
        println "FIELDS BROKEN"
        System.exit 0
    }

    // check if indexes match
    if (sorted[index] != version)
    {
        error "Index of '${version.version}' (${sorted[index]}) doesnt match vals actual index in the sorted list. Should be ${sorted.indexOf version.version}"
    }

    // check status for enums
    def status = version.status.toUpperCase();

    // check if its upper case
    if (version.status != status)
    {
        error "Status '${version.status}' for version ${version.version} is not all upper case"
    }
    // check for valid statuses
    else if (!(["FINE", "BROKEN", "OUTDATED"].contains(status))) // not in the list?
    {
        error "Status '${version.status}' for version ${version.version} is not a valid status"
    }

    // check 'outdated' status
    if (version.status == "OUTDATED")
    {
        if (!version.changes)
        {
            warn "'${version.version}' is marked 'OUTDATED' but there are no changes listed"
        }
    }
    // check 'broken' status
    else if (version.status == "BROKEN")
    {
        if (!version.bugs)
        {
            warn "'${version.version}' is marked 'BROKEN' but there are no bugs listed"
        }
    }
    else
    {
        // verify list position
        if (index != result.versions.size()-1)
        {
            error "'${version.version}' is not marked 'outdated' even though there is a newer version"
        }

        // check for changes
        if (version.changes.size() <= 0)
        {
            warn "'${version.version}' is the newest version, but has no defined changes"
        }
    }

    if (!version.docUrl)
    {
        warn "'${version.version}' doesnt have a 'docUrl' defined!"
    }
    else if (!version.docUrl.startsWith('http://') && !version.docUrl.startsWith('https://'))
    {
        warn "'${version.version}' has a 'docUrl' that isnt an HTTP URL"
    }


    // TODO: check the plugin-specific stuff
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
