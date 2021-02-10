# Mojtopia

This project aims to use a Yatopia-like build system, but remapping to mojang's mappings via https://github.com/MiniDigger/Toothpick.

But as of now, the aim of the project is simply to allow having Yatopia (or any other Paper fork) as an upstream instead of just Paper.

## Building

This uses gradle. Interesting Tasks:
* `setupUpstream` -> does paper stuff
* `mojangMappings` -> does the special juice
* `applyPatches` -> applies toothpick patches ontop of mojang mappings
* `rebuildPatches` -> rebuilds patches, duh
* `cleanUp` -> deletes everything in the work dirs and you will have to run ^ again

You can run those in both windows and linux env, but you shouldn't fix and match (so once you ran `setupUpstream` in WSL you can't run it again in windows until you run `cleanUp`)

### Steps for building

Pre-requirements
- JDK-11+
- Maven (and Gradle)
- setup JAVA_HOME

1. `git clone https://github.com/CadixDev/Lorenz/` (could require JDK-1.8 for compiling)
2. `cd Mercury/ && ./gradlew build install`
3. After building the dependencies `git clone https://github.com/MiniDigger/Toothpick`
4. run (under Windows use `./gradle.bat` instead of `./gradlew`) 
    1. `./gradlew setupUpstream`
    2. `./gradlew mojangMappings`
    3. `./gradlew applyPatches`
    4. `./gradlew shadowJar`

## Acknowledgements
 * [Lorenz](https://github.com/CadixDev/Lorenz) to read, write and convert between different mapping formats
 * [Mercury](https://github.com/CadixDev/Mercury) for applying those mappings to the paper source
 * [Atlas](https://github.com/CadixDev/Atlas) for applying those mappings to the vanilla server jar
 * [ToothPick](https://github.com/MiniDigger/Toothpick)