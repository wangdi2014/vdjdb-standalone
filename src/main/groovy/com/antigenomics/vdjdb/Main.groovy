/*
 * Copyright 2015 Mikhail Shugay
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.antigenomics.vdjdb

import com.antigenomics.vdjdb.impl.ClonotypeDatabase
import com.antigenomics.vdjdb.stat.ClonotypeSearchSearchSummary
import com.antigenomics.vdjdb.stat.Counter
import com.antigenomics.vdjtools.io.SampleWriter
import com.antigenomics.vdjtools.sample.Sample
import com.antigenomics.vdjtools.sample.SampleCollection
import com.antigenomics.vdjtools.sample.metadata.MetadataTable
import com.antigenomics.vdjtools.util.ExecUtil

import static com.antigenomics.vdjdb.Util.resourceAsStream

def DEFAULT_PARAMERES = "2,1,1,2"
def cli = new CliBuilder(usage: "vdjdb [options] " +
        "[sample1 sample2 sample3 ... if -m is not specified] output_prefix")
cli.h("display help message")
cli.m(longOpt: "metadata", argName: "filename", args: 1,
        "Metadata file. First and second columns should contain file name and sample id. " +
                "Header is mandatory and will be used to assign column names for metadata.")
cli._(longOpt: "search-params", argName: "s,i,d,t", args: 1,
        "CDR3 sequence search parameters: " +
                "allowed number of substitutions (s), insertions (i), deletions (d) and total number of mutations. " +
                "[default=$DEFAULT_PARAMERES]")
cli._(longOpt: "database", argName: "string", args: 1, "Path and prefix of an external database.")
cli._(longOpt: "summary", argName: "col1,col2,...", args: 1,
        "Table columns for summarizing, e.g. origin,disease.type,disease,source for default database.")
//cli._(longOpt: "filter", argName: "logical expression(__field__,...)", args: 1,
//        "Logical filter evaluated for database columns. Supports Regex, .contains(), .startsWith(), etc.")
cli.S(longOpt: "species", argName: "name", args: 1, required: true,
        "Species of input sample(s), e.g. human, mouse, etc.")
cli.R(longOpt: "chain", argName: "name", args: 1, required: true,
        "Receptor chain of input sample(s), e.g. TRA, TRB, etc.")
cli.v(longOpt: "v-match", "Require V segment matching.")
cli.j(longOpt: "j-match", "Require J segment matching.")
cli.c("Compressed output")

def opt = cli.parse(args)

if (opt == null || opt.h || opt.arguments().size() == 0) {
    cli.usage()
    System.exit(1)
}

// Check if metadata is provided

def metadataFileName = opt.m

if (metadataFileName ? opt.arguments().size() != 1 : opt.arguments().size() < 2) {
    if (metadataFileName)
        println "[ERROR] Only output prefix should be provided in case of -m"
    else
        println "[ERROR] At least 1 sample files should be provided if not using -m"
    cli.usage()
    System.exit(1)
}

// Remaining arguments

def dbPrefix = (String) (opt.'database' ?: null),
    p = (opt.'search-params' ?: DEFAULT_PARAMERES).split(",").collect { it.toInteger() },
    summaryCols = opt.'summary' ? ((String) opt.'summary').split(",") as List<String> : [],
    compress = (boolean) opt.c,
    vMatch = (boolean) opt."v-match", jMatch = (boolean) opt."j-match",
    species = (String) opt.S, chain = (String) opt.R,
//filter = opt.'filter' ?: null,
    outputFileName = opt.arguments()[-1]

def scriptName = getClass().canonicalName.split("\\.")[-1]

println "[${new Date()} $scriptName] Loading database..."

ClonotypeDatabase database

def metaStream = dbPrefix ? new FileInputStream("${dbPrefix}.meta") : resourceAsStream("vdjdb_legacy.meta"),
    dataStream = dbPrefix ? new FileInputStream("${dbPrefix}.txt") : resourceAsStream("vdjdb_legacy.txt")

database = new ClonotypeDatabase(metaStream, vMatch, jMatch, p[0], p[1], p[2], p[3])
database.addEntries(dataStream, species, chain)

println "[${new Date()} $scriptName] Finished.\n$database"

//
// Batch load all samples (lazy)
//

println "[${new Date()} $scriptName] Reading sample(s)..."

def sampleCollection = metadataFileName ?
        new SampleCollection((String) metadataFileName) :
        new SampleCollection(opt.arguments()[0..-2])

println "[${new Date()} $scriptName] ${sampleCollection.size()} sample(s) to process."

//
// Main loop
//

println "[${new Date()} $scriptName] Annotating sample(s) & writing results."

def sw = new SampleWriter(compress)

new File(ExecUtil.formOutputPath(outputFileName, "annot", "stats")).withPrintWriter { pwStats ->
    new File(ExecUtil.formOutputPath(outputFileName, "annot", "summary")).withPrintWriter { pwSummary ->
        def headerPrefix = [MetadataTable.SAMPLE_ID_COLUMN,
                            sampleCollection.metadataTable.columnHeader]
        pwSummary.println([headerPrefix,
                           "counter.type", summaryCols, "summary.count"].
                flatten().join("\t"))
        pwStats.println([headerPrefix,
                         "database",
                         "species", "chain", "counter.type",
                         "not.found", "found.once", "found.twice.and.more"].
                flatten().join("\t"))

        sampleCollection.eachWithIndex { Sample sample, int ind ->
            def sampleId = sample.sampleMetadata.sampleId

            def results = database.search(sample)

            def writer = sw.getWriter(ExecUtil.formOutputPath(outputFileName, sampleId, "annot"))

            writer.println(sw.header + "\tpenalty\t" + database.header)
            results.each { result ->
                result.value.each {
                    writer.println(sw.getClonotypeString(result.key) + "\t" +
                            it.result.penalty + "\t" + it.row.toString())
                }
            }

            writer.close()


            def summary = new ClonotypeSearchSearchSummary(database, summaryCols as List<String>, sample)
            summary.append(results)

            def prefix = [sampleId, sample.sampleMetadata.toString()],
                prefix1 = [prefix, dbPrefix ?: "default", species, chain].flatten()

            ["unique", "weighted"].each { counterType ->
                summary.listTopCombinations().each { List<String> combination ->
                    pwSummary.println([
                            prefix, counterType, combination,
                            summary.getCombinationCounter(combination).collect { Counter it -> it."${counterType}Count" }
                    ].flatten().join("\t"))
                }

                pwStats.println([
                        prefix1, counterType,
                        [summary.notFound, summary.foundOnce, summary.foundTwiceAndMore].collect { Counter it -> it."${counterType}Count" }
                ].flatten().join("\t"))
            }

            println "[${new Date()} $scriptName] ${ind + 1} sample(s) done."
        }
    }
}

println "[${new Date()} $scriptName] Finished."