# Offline Study Answer Solver

A 100%-offline Android app: import PDFs/DOCX/TXT, photograph a question, OCR it
on-device, retrieve relevant passages from your own materials with hybrid
(semantic + BM25) search, and generate an answer with a local LLM — with zero
network access and a hard refusal ("Answer not found in the uploaded
materials.") when the evidence isn't there.

## What's actually in this repo vs. what you must add locally

I built and wrote out every line of application logic — parsers, chunker,
Room database, hybrid retriever, OCR pipeline, prompt builder, anti-
hallucination logic, all UI screens, navigation, and the JNI contract for the
local LLM. That is the entire "brain" of the app.

Three things are physically impossible to hand you as files, because they
require an Android NDK/C++ toolchain and large binary downloads that this
environment has neither the tools nor the network access to produce, and
that's true of *any* on-device LLM engine (llama.cpp, MLC-LLM, MediaPipe LLM
Inference all have this same requirement — it's not specific to this
project):

1. **A compiled `libllama_bridge.so`** implementing the 3 JNI methods in
   `llm/LocalLlmEngine.kt`. Build steps below — about 15 minutes on any
   machine with Android Studio + NDK installed, done once.
2. **A quantized `.gguf` model file** (e.g. Phi-3-mini-4k-instruct-Q4_K_M,
   ~2.3GB, or a smaller 1-2B model for older phones). You supply this via the
   in-app "Import .gguf model file" button in Settings — it's a local file
   copy, matching the spec's "do not download models at runtime" rule.
3. **The embedding model's `.tflite` + `vocab.txt`** (e.g. a converted
   all-MiniLM-L6-v2), placed under `app/src/main/assets/models/`.

The app **detects the absence of each of these and shows the exact required
error strings from the spec** ("Offline AI model is not installed.",
"Required offline model is not installed.") rather than silently falling back
to anything online. Nothing in the codebase contains a network client at all
— see "Verifying offline-ness" below.

The `androidTest/OfflineVerificationTest.kt` file also references two small
test fixtures (`sample_test_material.pdf`, `sample_question.png`) that you'll
need to drop into `app/src/androidTest/assets/test/` yourself, plus a
`FileProvider` declaration in the manifest if you want that specific test to
run — the pipeline code it's testing is complete either way.

## Project structure

```
app/src/main/java/com/offlinestudy/solver/
├── ui/            (Compose screens: home, camera, question, answer, materials, search, settings)
├── ocr/           OcrEngine (ML Kit bundled/offline), ImagePreprocessor, QuestionSegmenter
├── documents/     PdfParser, DocxParser, TextParser, TextCleaner, HeadingDetector, DocumentIndexer
├── retrieval/     Chunker, EmbeddingEngine (TFLite), WordPieceTokenizer, VectorStore, Bm25, HybridRetriever
├── llm/           LocalLlmEngine (JNI), PromptBuilder, ModelManager, RagAnswerEngine, AnswerMode
├── database/      Room: DocumentEntity, ChunkEntity, DAOs, AppDatabase, Converters
├── util/          NetworkGuard
├── OfflineStudyApp.kt   (DI container)
├── AppViewModel.kt
└── MainActivity.kt      (Navigation)
```

## Why these specific offline choices

- **OCR**: ML Kit's *bundled* Latin text recognizer (`com.google.mlkit:text-recognition`)
  ships its model inside the APK at build time — not the "unbundled" Play
  Services variant, which can fetch model updates online. Zero runtime
  network calls either way.
- **PDF parsing**: PDFBox-Android (Apache-2.0), page-by-page, with local OCR
  fallback for scanned pages with no text layer.
- **DOCX parsing**: a small hand-written reader over `word/document.xml`
  using the platform's built-in `XmlPullParser` — avoids bundling all of
  Apache POI just to read paragraphs and heading styles.
- **Embeddings**: TensorFlow Lite running a converted sentence-transformer
  (e.g. MiniLM) fully on-device.
- **Vector store**: chunks + their embeddings live in the same Room/SQLite
  database as everything else; search is brute-force cosine similarity over
  an in-memory copy. There is no official prebuilt FAISS-for-Android
  artifact, so bundling FAISS would mean compiling and maintaining your own
  native build for a feature that brute-force already handles well at
  personal-study-library scale (thousands of chunks, sub-100ms search). If
  your library gets large enough to need it, add an IVF-style bucket index
  on top of the same table rather than switching stores.
- **LLM**: llama.cpp via JNI, GGUF quantized models. See build steps below.

## Building the native LLM engine (one-time, on your machine)

1. Install Android Studio with NDK + CMake (SDK Manager → SDK Tools).
2. Clone `https://github.com/ggml-org/llama.cpp` and open
   `examples/llama.android` in Android Studio — it already defines a JNI
   bridge (`llama-android` module) with load/generate/free functions
   equivalent to the contract in `llm/LocalLlmEngine.kt`.
3. Build the module (`./gradlew :llama:assembleRelease` or via Android
   Studio); this produces `libllama.so`/your bridge `.so` per ABI under
   `build/intermediates/.../jniLibs/<abi>/`.
4. Rename/wrap the exported JNI functions to match exactly:
   `Java_com_offlinestudy_solver_llm_LocalLlmEngine_nativeLoadModel`,
   `..._nativeGenerate`, `..._nativeFreeModel` (or add a thin adapter file —
   llama.cpp's own bridge uses slightly different method names/classes, so a
   ~30-line adapter `.cpp` is normal here).
5. Copy the resulting `.so` files into
   `app/src/main/jniLibs/arm64-v8a/libllama_bridge.so` (and `armeabi-v7a/` if
   you need 32-bit device support) in *this* project.
6. Pick a GGUF model (2-4B parameters, Q4_K_M quantization is a good
   phone-friendly default) and load it in-app via Settings → "Import .gguf
   model file".

## Setting up the embedding model

1. On a dev machine (one-time, online is fine here — this is the "Internet
   may be used during development/setup" allowance from the spec):
   ```
   pip install sentence-transformers onnx tf2onnx tensorflow
   python -c "
   from sentence_transformers import SentenceTransformer
   m = SentenceTransformer('sentence-transformers/all-MiniLM-L6-v2')
   m.save('minilm')
   "
   # convert minilm -> TensorFlow SavedModel -> embedding.tflite
   # (standard sentence-transformers -> TFLite conversion path)
   ```
2. Place the resulting `embedding.tflite` and the model's `vocab.txt` under
   `app/src/main/assets/models/`.
3. `EmbeddingEngine.DIM` is set to 384 to match MiniLM-L6-v2's output size —
   change it if you use a different embedding model.

## Build & run

```
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Verifying offline-ness (Section 25/30)

- `AndroidManifest.xml` **omits `android.permission.INTERNET` entirely**,
  which makes the OS itself refuse any outbound socket for this app's UID —
  the strongest possible guarantee, independent of what any dependency does.
- `grep -r "permission.INTERNET" app/src/main/AndroidManifest.xml` should
  return nothing; add that as a CI check so it fails loudly if anyone ever
  adds it back.
- `androidTest/OfflineVerificationTest.kt` asserts the permission is absent
  and exercises import → index → OCR → retrieve (→ generate, if a model is
  installed) end-to-end.
- Manual check: enable Airplane Mode, then run through the full flow —
  import a PDF, index it, photograph a question, get an answer with sources.

## Known gaps to close for a production build

- `Section: Save` button in the Answer screen is wired up but doesn't yet
  persist to a "Saved Answers" table — trivial addition (one more Room
  entity + DAO).
- Perspective correction in `ImagePreprocessor` is a crop-to-content +
  contrast pass, not a full 4-point homography solve; fine for photos taken
  reasonably square-on, but a heavily skewed photo will OCR worse. Swap in
  OpenCV's `findHomography` if you need robustness to sharp camera angles.
- "Answer All" for multi-question photos currently sends all questions as one
  combined prompt; splitting into N separate `RagAnswerEngine.answer()` calls
  (one per question, shown as a list) is straightforward if you want fully
  independent per-question retrieval and confidence scores.
