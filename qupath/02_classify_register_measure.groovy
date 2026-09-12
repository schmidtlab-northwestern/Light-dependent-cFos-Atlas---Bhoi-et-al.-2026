/*
 * 02_classify_register_measure.groovy
 * ---------------------------------------------------------------------------
 * Runs after 01_stardist_detect.groovy, once ABBA registration has been
 * imported into the QuPath project. Five steps:
 *
 *   1. Filter StarDist detections by nuclear morphology -> Positive / Ignore*
 *   2. Load the warped ABBA atlas annotations (Allen CCFv3, by acronym)
 *   3. Add a mean intensity measurement to every atlas annotation
 *   4. Flag annotations with poor tissue coverage as Exclude = 1
 *   5. Write CCF atlas coordinates onto every detection
 *
 * Run with: QuPath > Automate > Run for project.
 *
 * The `Exclude` flag written in step 4 is what notebook 01 reads to drop
 * annotations that fall on torn, folded, or off-section tissue. Annotations
 * outside the image bounds get no intensity measurement, so their value is
 * null, and null is treated as Exclude = 1.
 */

import net.imglib2.RealPoint
import qupath.lib.measurements.MeasurementList
import qupath.ext.biop.abba.AtlasTools
import qupath.lib.analysis.features.ObjectMeasurements
import static qupath.lib.gui.scripting.QPEx.*

// ===========================================================================
// CONFIG
// ===========================================================================
// Step 1 -- nuclear morphology filter
def diameterMin = 5.0      // microns
def diameterMax = 22.0     // microns
def circularityMin = 0.5

// Step 3/4 -- tissue coverage channel.
// This is the NeuN channel, which in this acquisition is imaged on FITC. The
// measurement key below must match the channel name in the image metadata.
def COVERAGE_CHANNEL_KEY = "FITC: Mean"
def coverageThresholdFraction = 0.25   // exclude below 25% of the section median
def requestedPixelSizeMicrons = 50.0   // resolution for the annotation intensity pass

// ===========================================================================
// STEP 1: Filter StarDist detections by morphology
// ===========================================================================
getDetectionObjects().forEach(detection -> {
    def ml = detection.getMeasurementList()
    def maxDiam = ml.get("Max diameter µm")
    def minDiam = ml.get("Min diameter µm")
    def circularity = ml.get("Circularity")

    if (maxDiam <= diameterMax && minDiam >= diameterMin && circularity >= circularityMin) {
        detection.setPathClass(getPathClass("Positive"))
    } else {
        detection.setPathClass(getPathClass("Ignore*"))
    }
})

// ===========================================================================
// STEP 2: Load ABBA atlas annotations
// ===========================================================================
AtlasTools.loadWarpedAtlasAnnotations(getCurrentImageData(), "acronym", true)

// ===========================================================================
// STEP 3: Add intensity measurements to annotations
// ===========================================================================
def server = getCurrentImageData().getServer()
def annotations = getAnnotationObjects()

int outOfBounds = 0
annotations.each { ann ->
    try {
        ObjectMeasurements.addIntensityMeasurements(
            server,
            ann,
            requestedPixelSizeMicrons,
            [ObjectMeasurements.Measurements.MEAN] as List,
            [] as List
        )
    } catch (Exception e) {
        // Annotation lies outside the image bounds, so no measurement is added.
        // Its coverage value stays null and step 4 marks it Exclude = 1.
        outOfBounds++
    }
}

println "Intensity measurements complete. ${outOfBounds} annotations were outside image bounds."
fireHierarchyUpdate()

// ===========================================================================
// STEP 4: Flag annotations by tissue coverage
// ===========================================================================
// The threshold is relative to the median of THIS section, not an absolute
// value, so it tracks section-to-section differences in staining intensity
// instead of excluding whole weakly-stained sections.
def validMeans = annotations.collect { ann ->
    ann.getMeasurementList().get(COVERAGE_CHANNEL_KEY)
}.findAll { it != null && !Double.isNaN(it) && it > 0 }

if (validMeans.isEmpty()) {
    println "WARNING: No valid '${COVERAGE_CHANNEL_KEY}' measurements found."
    println "Available keys on first annotation: ${annotations[0]?.getMeasurementList()?.getMeasurementNames()}"
    println "Check COVERAGE_CHANNEL_KEY at the top of this script against your channel names."
} else {
    validMeans.sort()
    double medianCoverage = validMeans[validMeans.size() / 2 as int]
    double cutoff = medianCoverage * coverageThresholdFraction

    println "Section ${COVERAGE_CHANNEL_KEY} median: ${medianCoverage}"
    println "Exclusion cutoff (${coverageThresholdFraction * 100}% of median): ${cutoff}"

    annotations.each { ann ->
        def val = ann.getMeasurementList().get(COVERAGE_CHANNEL_KEY)
        def exclude = (val == null || Double.isNaN(val) || val < cutoff) ? 1.0 : 0.0
        ann.getMeasurementList().put("Exclude", exclude)
        ann.getMeasurementList().close()
    }

    def excluded = annotations.count { it.getMeasurementList().get("Exclude") == 1.0 }
    println "Marked ${excluded} of ${annotations.size()} annotations as Exclude = 1"
    println "Marked ${annotations.size() - excluded} annotations as Exclude = 0"
}

fireHierarchyUpdate()

// ===========================================================================
// STEP 5: Compute atlas coordinates for every detection
// ===========================================================================
def pixelToAtlasTransform = AtlasTools
    .getAtlasToPixelTransform(getCurrentImageData())
    .inverse()

getDetectionObjects().forEach(detection -> {
    RealPoint atlasCoordinates = new RealPoint(3)
    MeasurementList ml = detection.getMeasurementList()
    atlasCoordinates.setPosition([
        detection.getROI().getCentroidX(),
        detection.getROI().getCentroidY(),
        0
    ] as double[])
    pixelToAtlasTransform.apply(atlasCoordinates, atlasCoordinates)
    ml.put("Atlas_X", atlasCoordinates.getDoublePosition(0))
    ml.put("Atlas_Y", atlasCoordinates.getDoublePosition(1))
    ml.put("Atlas_Z", atlasCoordinates.getDoublePosition(2))
})

println "Done. Atlas coordinates assigned to ${getDetectionObjects().size()} detections."
