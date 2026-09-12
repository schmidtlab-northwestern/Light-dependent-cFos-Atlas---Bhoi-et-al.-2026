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

Region filtering runs in three passes, and the order matters: drop the non-neural
top-level divisions (fiber tracts, ventricular systems), drop numbered cortical
layer subdivisions, then drop any region whose children are all individually
present. Ancestry has to be resolved last, because which rows count as leaves
depends on what the first two passes removed.

Writes `data/compiled/density_by_region_batch.csv` and, in
`data/compiled/cleaned_output/`, the wide (regions × mice) and long
(one row per region per mouse) leaf tables.

### Stage 2 — The confirmatory gate

**`notebooks/02_global_model_specification_grid.ipynb`**

One scalar per mouse, `~ genotype * sex * light`, with the three-way interaction
as the primary confirmatory statistic. No multiplicity problem and no per-region
machinery.

Four definitions of "whole-brain c-Fos" (unweighted mean over log regional
densities, volume-weighted, root-annotation density, batch-residualized) are each
crossed with two batch treatments, and every specification is fitted. The
specification curve at the end shows the three-way estimate under all of them,
with the full range across leave-one-mouse-out and leave-one-batch-out fits drawn
behind it. A result that survives there is robust to the scalar definition, the
batch treatment, and any single animal or batch.

Also runs an optimizer and start-value check — four optimizers plus random starts,
with non-converged fits excluded from the pass/fail range rather than pooled into it.

The `root` specifications need `data/compiled/cleaned_output/density_root.csv`.
If that file is absent the `root` specs are skipped with a message rather than
silently substituted. Section 4a cross-checks contrast names against notebook 03's
output; it is skipped on a first run and passes once notebook 03 has been run.

Outputs to `results/global_model/`.

### Stage 3 — Per-region models, permutation, and enrichment

**`notebooks/03_per_region_lmm_permutation_enrichment.ipynb`**

Fits `log_density ~ genotype * sex * light` per region, extracts each reported
contrast as a weighted sum over the eight design-cell means, and BH-FDR corrects.
Then asks
the two aggregate questions that per-region FDR cannot: is the effect **broad**
across regions, and is it **concentrated in particular anatomy**.

Three things worth knowing before reading the output:

- **The permutation unit is always the mouse**, shuffled once per permutation and
  propagated to every region. Shuffling region-by-region would destroy the
  between-region correlation that is genuinely present and make the null far too
  narrow.
- **Interaction terms use Freedman–Lane** (permuting residuals from the reduced
  model), not label shuffling. A plain label shuffle tests a composite null and is
  close to powerless for an interaction when real lower-order effects are present.
- **Enrichment claims are led by the spatial null**, not the random one. Naive
  hypergeometric or Fisher tests on atlas data are badly false-positive-inflated
  by spatial autocorrelation (Fulcher et al. 2021). The spatial null draws
  size-matched *contiguous* region sets — the discrete-parcellation analogue of a
  spin test. The random null is reported alongside, but an anatomically contiguous
  set will beat scattered random sets for reasons that have nothing to do with
  biology, so the spatial number is the honest one.

Outputs to `results/per_region/`. Runtime is a few minutes at the default
`N_PERM = 2000`.

### Stage 4 — Contrast PLSC and Figure 5

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

Three matrices are in play and they are not interchangeable; every cell states
which it uses:

| Matrix | Contents |
|---|---|
| `Xl` | log density, raw |
| `Xa` | `Xl` with immunostaining batch BLUPs removed |
| `Xz` | `Xa`, per-mouse centered, then region z-scored |

Per-mouse centering is what makes the main path a test of **redistribution**
rather than a restatement of the global result. Panels 5A–5C deliberately use
`Xl` instead: the intercept of the dark/light fit *is* the amplitude estimate, so
centering would set the measured quantity to zero by construction.

Each panel writes a tidy CSV next to the figure, so any panel can be rebuilt in
Prism or Illustrator without re-running. The matplotlib output is for checking
numbers, not for the manuscript.

Outputs to `results/figures/` and `results/figure_data/`. At the default
`N_PERM = N_BOOT = 10,000` this takes roughly 10–20 minutes.

### Stage 5 — Atlas heatmaps

**`notebooks/05_atlas_heatmaps.ipynb`**

Renders the per-region values exported by notebook 04 onto a coronal-slice grid,
with optional independent data on each hemisphere.

`RUN_TAG` must match the tag notebook 04 printed — the panel CSV filename embeds
it. Notebook 04 prints its `RUN_TAG` in its first cell; the default is
`cellmean_gainoff`. `PANEL` selects which export to render (`5H_light` or
`5I_three_way`) and `VALUE_COL` selects which column is mapped.

Rendering uses `allen_mouse_10um` for finer boundaries, while the analysis
notebooks use `allen_mouse_25um`. The ontology is identical between the two, so
acronyms match and no statistic depends on the choice — but Methods should state
both resolutions rather than implying one was used throughout.

Outputs to `results/figures/atlas/`.

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

Dropped are the three pooled two-ways (superseded by their split forms, which is
what Figure 2D reports) and the four `M_vs_F` simple effects (no figure reports a
per-region sex simple effect). All three split *pairs* are kept in full, including
`geno:sex | light` alongside `geno:sex | dark`: the two levels of a split are one
decomposition of the three-way — their difference is the three-way, their average
is the pooled term — so keeping one without the other would leave the
decomposition asymmetric. Because BH-FDR is applied within each contrast across
regions and never across contrasts, dropping a contrast cannot move the q-value
of any contrast that is kept.

**Contrasts are always weight vectors over the eight design cells**, projected
through each model's own fitted cell design matrix. Unifying every contrast type
— simple effect, two-way, three-way, main effect — onto one mechanism guarantees a
consistent convention instead of risking drift between separately derived methods.
It also means the treatment coding of any individual fit never touches an estimate
or a standard error.

**Logs are natural logs in the models.** Volcano plot axes are log₂. Both appear
in the outputs, so check the column name before quoting a number.

**Densities are log-transformed with a pseudocount** equal to half the smallest
non-zero density in the matrix, computed once and reused everywhere.

**`### CHOICE ###` and `### FLAG ###` markers** appear inline throughout the
notebooks. `CHOICE` marks a consequential analytical decision with the reasoning
attached; `FLAG` marks a known limitation or something that must be stated
carefully in the manuscript. They are worth reading — most of the non-obvious
methodology is documented there rather than here.

---

## Caveats carried in the code

These are documented at the point of use in the notebooks and repeated here so
they are not missed:

- **Global gain is a real confound.** Per-animal whole-brain mean log density
  explains the large majority of per-region contrast estimates, and one
  immunostaining batch composed entirely of one genotype group had the lowest
  gains. No permutation test can separate a genuine regional effect from a
  technical gain difference confounded with group assignment. This is what
  motivates the gain-removal analysis in notebook 04 and the gain diagnostics in
  notebook 03.
- **PLSC bootstrap ratios are a stability measure, not a significance test**, and
  they are uncorrected across regions. The usual normal-theory chance rate for
  `|BSR| > 2` is 4.6%; simulation with these exact cell sizes gives roughly double
  that, from small-n bootstrap SE bias. Language must be "stable contributors".
- **Sex-involving permutation contrasts use an invalid null.** Sex was never
  assigned by anyone, so those nulls cannot fully null an interaction. They are
  reported as untested rather than as significance. This includes the three-way in
  the PLSC panel.
- **PLSC-nominated regions are exploratory.** Feeding them back into confirmatory
  reads would be circular, and they are labelled accordingly.
- **Label-permutation contrasts have a resolution floor.** With few mice per
  stratum the number of distinct labellings can be smaller than `N_PERM`, and no
  number of extra permutations pushes *p* below `1/(n_distinct + 1)`. The floor is
  printed alongside each result and should be reported rather than written as
  `p < .001`.

---

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
