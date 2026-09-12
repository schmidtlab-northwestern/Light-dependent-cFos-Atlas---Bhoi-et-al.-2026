# Sex-Dependent Modulation of Whole-Brain cFos Expression by Light and Melanopsin in the Mouse

Analysis code and data for the whole-brain cFos atlas study.

The experiment is a fully crossed 2 × 2 × 2 design — **sex** (M/F) × **genotype**
(melanopsin knockout, `Opn4^Cre/Cre`, vs. control, `Opn4^Cre/+`) × **light**
(light/dark) — across 40 mice. Light-induced c-Fos density is quantified in every
Allen CCFv3 region, and the primary confirmatory statistic is the three-way
interaction.

Everything needed to reproduce the analysis from raw region-level exports through
to the manuscript figures is in this repository, including the input data.

---

## Repository layout

```
.
├── qupath/                          # Image analysis (QuPath 0.6 + ABBA, Groovy)
│   ├── 01_stardist_detect.groovy
│   ├── 02_classify_register_measure.groovy
│   └── 03_export_measurements.groovy
├── notebooks/                       # Statistical analysis (Python / Jupyter)
│   ├── 01_atlas_processing.ipynb
│   ├── 02_global_model_specification_grid.ipynb
│   ├── 03_per_region_lmm_permutation_enrichment.ipynb
│   ├── 04_contrast_plsc_figure5.ipynb
│   └── 05_atlas_heatmaps.ipynb
├── data/
│   ├── raw_annotations/             # Per-mouse QuPath exports (the raw data)
│   ├── compiled/                    # Compiled density matrices
│   │   ├── density_by_region_batch.csv
│   │   └── cleaned_output/
│   │       ├── density_by_region_leaves_wide.csv
│   │       └── density_by_region_leaves_long.csv
│   └── mouse_info_template.csv      # Per-mouse metadata
├── results/                         # All notebook outputs land here (gitignored)
├── requirements.txt
└── README.md
```

---

## Data availability

All input data is in this repository. You do not need to run the image analysis,
or obtain anything separately, to reproduce every statistic and figure.

| What | Where | Used by |
|---|---|---|
| **Raw data** — per-mouse QuPath annotation exports, one CSV per animal, one row per atlas annotation per section | `data/raw_annotations/` | input to notebook 01 |
| **Compiled dataset** — region × mouse density matrices, before and after region filtering | `data/compiled/` | notebooks 02, 03, 04 |
| **Mouse info template** — sex, genotype, light condition, batch structure | `data/mouse_info_template.csv` | notebooks 02, 03, 04 |

The compiled files in `data/compiled/` are exactly what notebook 01 produces from
`data/raw_annotations/`. They are committed so the statistical notebooks can be
run directly; re-running notebook 01 regenerates them in place. Either entry
point gives the same downstream results.

### `mouse_info_template.csv` schema

One row per mouse, joined to the density matrices on `id`.

| Column | Values | Notes |
|---|---|---|
| `id` | string | Must match the mouse IDs recovered from the raw filenames |
| `genotype` | `cre/+`, `cre/cre` | `cre/+` = control, `cre/cre` = melanopsin knockout (MKO) |
| `sex` | `F`, `M` | |
| `light` | `D`, `L` | Dark or light exposed |
| `batch` | string | Immunostaining batch. Carried as a random intercept in the global model |
| `perfusion_batch` | string | Sex-nested. Used for the PLSC row-permutation scheme and leave-one-batch-out only |
| `litter` | string | Reported, not modelled |
| `age_days` | integer | Reported, not modelled |

The two batch variables are **not** interchangeable and the notebooks keep them
separate throughout. Immunostaining batch is the staining/technical axis;
perfusion batch is nested within sex, which is why it can only be used for
permutation restriction and sensitivity analysis, never as a covariate that would
absorb the sex effect.

---

## Installation

```bash
git clone <repository-url>
cd <repository-name>

python -m venv .venv
source .venv/bin/activate        # Windows: .venv\Scripts\activate
pip install -r requirements.txt

jupyter lab
```

Python 3.10 or newer. Tested on 3.13.

On first run, `brainglobe-atlasapi` downloads the Allen CCFv3 annotation volumes
(`allen_mouse_25um` for the analysis notebooks, `allen_mouse_10um` for rendering
in notebook 05) into `~/.brainglobe`. That is a few hundred MB and happens once.
Notebook 01 additionally fetches the Allen structure graph live from the Allen
Brain Atlas RMA API, so it needs a network connection.

`allensdk` is deliberately not a dependency — it does not support Python 3.13.
The ontology is reached through the RMA API and brainglobe instead.

---

## Running the analysis

Notebooks resolve their own paths. Each finds the repository root by walking up
from the working directory until it sees a folder containing both `data/` and
`notebooks/`, so opening them from anywhere inside the checkout works with no
configuration. If you keep the data elsewhere, set `PROJECT_ROOT` by hand in the
path block at the top of each notebook and everything below follows.

Run them in order. Notebooks 02–04 each read only the compiled data and the mouse
info table, so they are independent of one another and can be run in any order
among themselves; notebook 05 depends on notebook 04.

### Stage 0 — Image analysis (optional)

`qupath/` holds the Groovy scripts that produced `data/raw_annotations/`. You only
need these to go back to the raw images, which are not distributed here.

| Script | What it does | How to run |
|---|---|---|
| `01_stardist_detect.groovy` | StarDist nuclear detection on the cFos (TRITC) channel | Automate → Run for project |
| `02_classify_register_measure.groovy` | Morphology filter, load warped ABBA atlas annotations, add NeuN-channel coverage measurements, flag poor-coverage annotations as `Exclude`, write CCF coordinates | Automate → Run for project |
| `03_export_measurements.groovy` | Export annotation and detection measurements to CSV | Script editor → Run, **once** per project |

Requires QuPath 0.6, the StarDist extension with the `dsb2018_heavy_augment.pb`
model, and ABBA for atlas registration. Set `MODEL_PATH` in script 01 and
`EXPORT_ROOT` in script 03 before running. One QuPath project per mouse, named
with the mouse ID — notebook 01 recovers each mouse ID from the exported filename.

### Stage 1 — Build the density matrices

**`notebooks/01_atlas_processing.ipynb`**

Reads `data/raw_annotations/*.csv`, drops annotations flagged `Exclude = 1`, sums
counts and areas per region per mouse, converts to density (cells/mm²), and joins
the Allen ontology.

### Stage 2 

**`notebooks/02_global_model_specification_grid.ipynb`**

One scalar per mouse, `~ genotype * sex * light`, with the three-way interaction
as the primary confirmatory statistic. No multiplicity problem and no per-region
machinery.

### Stage 3 — Per-region models, permutation, and enrichment

**`notebooks/03_per_region_lmm_permutation_enrichment.ipynb`**

Fits `log_density ~ genotype * sex * light` per region, extracts each reported
contrast as a weighted sum over the eight design-cell means, and BH-FDR corrects.
Then asks
the two aggregate questions that per-region FDR cannot: is the effect **broad**
across regions, and is it **concentrated in particular anatomy**.

### Stage 4 — PLSC

**`notebooks/04_contrast_plsc_figure5.ipynb`**

Main text: unrotated **contrast task PLSC**. `R = CᵀX` is built directly from the
seven orthogonal design contrasts, and each row-normalized row is that contrast's
brain salience vector. No SVD, so no rotation, so every contrast keeps its own
identity instead of being mixed across latent variables. The row norm is the
multivariate magnitude statistic; a per-region OLS on the same normalized data
supplies per-region inference.

Supplement (Section 11): conventional mean-centered task PLS with SVD and latent
variables, the variant used by the BraiAn pipeline (Chiaruttini et al. 2025,
*Cell Rep* 44:115876). Included for comparison, not for inference, and run on the
same input so any difference is attributable to the method.

### Stage 5 — Atlas heatmaps

**`notebooks/05_atlas_heatmaps.ipynb`**

Renders the per-region values exported by notebook 04 onto a coronal-slice grid,
with optional independent data on each hemisphere.

---

## Conventions that apply throughout

**Sign convention is control-positive.** `cre/+ = +1`, `M = +1`, `L = +1`, in
every notebook. A positive `geno:light` estimate means the light effect is more
positive in control than in MKO. A positive three-way means the sex difference in
the light response is more positive in control than in MKO. Notebooks 02 and 03
build every contrast from the same `code_map` and notebook 02 asserts the two
weight sets are identical element by element, so the pipelines cannot silently
drift apart.

**Eighteen contrasts are reported.** Notebooks 02 and 03 construct the full set
of 25 — twelve simple effects, three pooled two-ways, six split two-ways, the
three-way, and three main effects — because the structural assertions need the
pooled terms and both levels of every split pair to verify the coding convention.
They then restrict to the eighteen the manuscript reports, listed in the
`PAPER_CONTRASTS` block of each notebook: three main effects, the three-way, six
split two-ways, and eight simple effects. Both notebooks use the identical list,
which is what notebook 02's §4a cross-check verifies.

## Citation

<!-- Fill in once the preprint or paper is live. -->

Bhoi, J., et al. *Sex-Dependent Modulation of Whole-Brain cFos Expression by Light
and Melanopsin in the Mouse.*

### Software this analysis depends on

- **Allen CCFv3** — Wang et al. (2020), *Cell* 181:936–953.
- **QuPath** — Bankhead et al. (2017), *Sci Rep* 7:16878.
- **StarDist** — Schmidt et al. (2018), *MICCAI*.
- **ABBA** — Chiaruttini et al., Aligning Big Brains & Atlases.
- **BrainGlobe** — Claudi et al. (2020), *J Open Source Softw* 5:2668.
- **Spatial null framework** — Fulcher et al. (2021), *Nat Commun* 12:2669.
- **Freedman–Lane permutation** — Freedman & Lane (1983); Winkler et al. (2014),
  *NeuroImage* 92:381–397.
- **Task PLS comparison** — Chiaruttini et al. (2025), *Cell Rep* 44:115876.

---

## Contact

Questions about the code or the data: open an issue on this repository.
