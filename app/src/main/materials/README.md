# Materials Source Files

This directory contains the raw Filament material definition files (`.mat`).

## Available Materials

| File | Material Name | Shading Model | Description |
|------|--------------|---------------|-------------|
| `color.mat` | `ColorMaterial` | `lit` | Base material for standard mesh rendering with customizable RGBA color. |

---

## Important: Version Compatibility

> **Warning**
> `.filamat` binaries are strictly tied to the version of the Filament library used in the application.

- You **must** download and use the `matc` binary that matches the exact version of the Filament SDK specified in `build.gradle.kts` (e.g. from the official [Filament Releases](https://github.com/google/filament/releases)).
- Whenever you update the Filament dependency version in the Android project, you **must recompile** all `.mat` files using the updated `matc` executable.
- Mismatched material versions will cause runtime failures when loading assets in the app.

---

## Compiling Materials

Compile `.mat` files into `.filamat` binaries using the Filament Material Compiler (`matc`):

```bash
matc -o ../assets/materials/color.filamat color.mat
```

For a debug build with additional logs:

```bash
matc -g -o ../assets/materials/color.filamat color.mat
```

---

## Gradle Build Automation

To avoid manually compiling `.mat` files, you can generate `.filamat` files automatically during the Android build process.

### Why this approach?

Using:

```kotlin
executable = "matc"
```

is not portable because:

- `matc` may not be available in the system `PATH`.
- Windows uses `matc.exe`.

The following Gradle task provides a more resilient cross-platform solution.

### Example Configuration

Add the following to `app/build.gradle.kts`:

```kotlin
import org.apache.tools.ant.taskdefs.condition.Os

tasks.register<Exec>("compileFilamentMaterials") {
    group = "build"
    description = "Compiles .mat files to .filamat using matc"

    val matcExecutable =
        if (Os.isFamily(Os.FAMILY_WINDOWS)) "matc.exe" else "matc"

    executable =
        project.findProperty("matc.path")?.toString()
            ?: matcExecutable

    args(
        "-o",
        "${projectDir}/src/main/assets/materials/color.filamat",
        "${projectDir}/src/main/materials/color.mat"
    )

    // Allows builds to succeed when matc is not installed.
    // In that case the precompiled .filamat file from assets is used.
    isIgnoreExitValue = true
}

tasks.named("preBuild") {
    dependsOn("compileFilamentMaterials")
}
```

### Custom `matc` Location

If `matc` is installed in a custom location, define it in `local.properties`:

```properties
matc.path=/path/to/filament/bin/matc
```

> **Note**
>
> `local.properties` is machine-specific and should not be committed to version control.
