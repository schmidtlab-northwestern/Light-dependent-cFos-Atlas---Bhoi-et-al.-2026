/*
 * 03_export_measurements.groovy
 * ---------------------------------------------------------------------------
 * Exports annotation- and detection-level measurements for a whole QuPath
 * project to CSV. Run ONCE per project, after 02_classify_register_measure.groovy
 * has been run for every image.
 *
 * Run with: QuPath > Automate > Script editor > Run (NOT "Run for project" --
 * MeasurementExporter already walks the full image list itself).
 *
 * The annotation CSV is the input to notebook 01. Copy the exported
 * `<project>_annotation_measurements.csv` files into `data/raw_annotations/`,
 * one per mouse. Notebook 01 recovers each mouse ID from the filename, so the
 * QuPath project name must be the mouse ID.
 *
 * The detection CSV carries per-cell atlas coordinates. Nothing in the current
 * analysis consumes it; it is exported so point-level analyses stay possible
 * without re-running detection.
 */

import qupath.lib.gui.tools.MeasurementExporter
import qupath.lib.objects.PathAnnotationObject
import qupath.lib.objects.PathDetectionObject

// ===========================================================================
// CONFIG -- set your export folders here
// ===========================================================================
def EXPORT_ROOT = "/path/to/exports"

def annotationExportDir = new File(EXPORT_ROOT, "annotation")
def detectionExportDir  = new File(EXPORT_ROOT, "detection")

// ===========================================================================

annotationExportDir.mkdirs()
detectionExportDir.mkdirs()

def project = getProject()
def projectName = project.getPath().toFile().parentFile.getName()

// ---------------------------------------------------------------------------
// Annotation measurements -- one row per atlas region per section.
// `Exclude` is the flag written by script 02 and read by notebook 01.
// ---------------------------------------------------------------------------
def annotationFile = new File(annotationExportDir, "${projectName}_annotation_measurements.csv")

new MeasurementExporter()
    .imageList(project.getImageList())
    .exportType(PathAnnotationObject.class)
    .includeOnlyColumns("Image", "Name", "Area µm^2", "Num Positive", "FITC: Mean", "Exclude")
    .separator(",")
    .exportMeasurements(annotationFile)

println "Annotation export complete: ${annotationFile}"

// ---------------------------------------------------------------------------
// Detection measurements -- one row per detected nucleus, with CCF coordinates.
// ---------------------------------------------------------------------------
def detectionFile = new File(detectionExportDir, "${projectName}_detection_measurements.csv")

new MeasurementExporter()
    .imageList(project.getImageList())
    .exportType(PathDetectionObject.class)
    .includeOnlyColumns("Image", "Classification", "Parent", "Detection probability",
                        "Area µm^2", "Max diameter µm", "Min diameter µm",
                        "Atlas_X", "Atlas_Y", "Atlas_Z")
    .separator(",")
    .exportMeasurements(detectionFile)

println "Detection export complete: ${detectionFile}"
println "All done."
