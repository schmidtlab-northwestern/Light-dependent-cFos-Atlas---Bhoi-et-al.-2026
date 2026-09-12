/*
 * 01_stardist_detect.groovy
 * ---------------------------------------------------------------------------
 * StarDist nuclear detection on the cFos (TRITC) channel.
 *
 * Run with: QuPath > Automate > Run for project, so every image in the project
 * is processed with identical settings.
 *
 * Requires the QuPath StarDist extension and the `dsb2018_heavy_augment.pb`
 * model, available from the StarDist model zoo:
 *   https://github.com/qupath/models
 *
 * Replaces the previous stardist4_cFos_MAC.groovy / stardist4_cFos_windows64.groovy
 * pair, which were identical apart from the hardcoded model path. Set MODEL_PATH
 * below for your machine instead.
 */

import qupath.ext.stardist.StarDist2D
import qupath.lib.scripting.QP

// ===========================================================================
// CONFIG -- set MODEL_PATH to wherever you saved the StarDist model
// ===========================================================================
// macOS / Linux example:  "/path/to/dsb2018_heavy_augment.pb"
// Windows example:        "C:\\path\\to\\dsb2018_heavy_augment.pb"
def MODEL_PATH = "/path/to/dsb2018_heavy_augment.pb"

def CFOS_CHANNEL = "TRITC"   // channel carrying the cFos signal
def PROB_THRESHOLD = 0.25    // detection probability threshold
def PIXEL_SIZE = 1           // resolution (microns) at which detection runs

// ===========================================================================

if (!new File(MODEL_PATH).exists()) {
    println "ERROR: StarDist model not found at ${MODEL_PATH}"
    println "Edit MODEL_PATH at the top of this script."
    return
}

QP.removeDetections()

def stardist = StarDist2D
    .builder(MODEL_PATH)
    .channels(CFOS_CHANNEL)
    .preprocessGlobal(               // normalize across the whole image, not per-tile
        StarDist2D.imageNormalizationBuilder()
            .maxDimension(4096)      // downsample large images to this max width/height
            .percentiles(0.2, 99.8)  // percentiles used for normalization
            .build()
    )
    .threshold(PROB_THRESHOLD)
    .pixelSize(PIXEL_SIZE)
    .cellExpansion(0)                // nuclei only; no cytoplasmic expansion
    .includeProbability(true)        // keep prediction probability as a measurement
    .measureShape()                  // shape measurements -- needed by script 02
    .measureIntensity()
    .build()

def imageData = QP.getCurrentImageData()

// Detect within top-level annotations; if there are none, use the whole image.
def pathObjects = QP.getAnnotationObjects().findAll { it.getParent().isRootObject() }
if (pathObjects.isEmpty()) {
    QP.createFullImageAnnotation(true)
    pathObjects = QP.getAnnotationObjects()
}

stardist.detectObjects(imageData, pathObjects)
stardist.close()   // release memory

println("Done! - " + imageData.getServer().getMetadata().getName())
