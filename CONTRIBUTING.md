# Contributing to Gadgets & Gizmos

Thanks for taking the time to contribute.

## Branches
- `main`
    - Release-ready tested code.
    - Merged alongside CurseForge/Modrinth releases.
- `inDev`
    - Active development branch.
    - May contain unfinished, unreleased, or experimental API changes.

## Before opening a pull request

- Make sure the project builds successfully with:
    - Bash
```bash
./gradlew clean build
```
    - Windows
```bat
gradlew clean build
```

- Test your changes in-game.
- Do not commit build output, IDE files, temporary files, backups, or decompiled reference code.
- Keep changes focussed on the problem being solved.
- Avoid unrelated refactors in the same pull request

## Library Boundaries
Code in the library must not depend on Gadgets & Gizmos addon classes or other external mod implementation classes unless stated otherwise.

Library code belongs library and not in the Gadgets and Gizmos mod. [You can find the library repo here](https://github.com/Riieno/Gadgets-And-Gizmos-Library)

Do not register over another mod's namespace.
Use strict registration methods unless replacement is explicitly required.

## Code style
The library and G&G main use a specific and intentionally explicit style, please try to stay consistent with that.

## Formatting
Opening braces should stay on the same line
```java
if(condition){
    doSomething();
}
```

Short guards should be used and remain on one line when they remain readable.
```java
if(value == null) return;
```

Avoid reformatting unrelated files to match a different format.

## Naming
Use clear names, but don't over-expand simple local variables for no reason.
Examples of variables used in the project include:
```java
context = ctx
previous = prev
result = res
index = idx
value = val
```

Public API names should be descriptive and stable.

## Comments
Comments should explain what the code is doing in plain language and not be over descriptive.
For example:
```java
// Get the retained sublevel ids
public Set<UUID> retainedSubLevelIds(){
    return Set.copyOf(retained.keySet());
}
```

or:

```java
// Remove the old ticket before retaining renamed ticket
invokeTicket(subLevel, LEGACY_TICKET, owner, false);
```

Do not add comments that read like task notes, placeholders, or unfinished work.
`TODO`, `FIXME`, `DO FIX`, and similar comments are not accepted in pull requests and may result in the request getting denied.
Pull requests containing unfinished work WILL NOT be merged.
A pull request should be completed working code, not a partial implementation.
Avoid comments like:
```java
// TODO: Add support for x
// Need to fix this later
// DO FIX
// FIXME: This breaks under x
```

## Section Headers
The library and G&G use large section headers in larger classes.
```java
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        CONSTANTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/


/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/


/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        HELPERS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

```

Use the same pattern when adding a new major section to a class that already follows it.

Do not add large section headers to tiny files where they would add noise.
For example:
```java
package com.rieno.gadgetsandgizmos.client;

import com.some.dep.SomeDependency;

public final class SomeClass{
    // Do something
};

```

## Threading and logical side

Respect Minecraft and NeoForge logical-side rules.

- Physics, topology, storage, shipping and control state are server-side systems.
- Rendering belongs in client-only packages.
- Do not load client-only implementation classes from common/server code.
- Mutate levels and block entities on the owning game thread.
- Treat unloaded SubLevels and missing block entities as normal unavailable states.

## Pull Requests
A good pull request should explain:
- What changed.
- Why the change is needed.
- What is affected.
- Whether existing  behavior or saved data changes.
- How the change was tested.

Screenshots and/or videos are useful for rendering, GUI, physics, or interaction changes.

## Bugs and Issues

When reporting a bug, include as much information as possible:
- Gadgets & Gizmos Version.
- Minecraft Version.
- NeoForge Version.
- Relevant Dependency Versions.
- Reproduction steps.
- Expected Behaviour.
- Actual Behaviour.
- Logs and/or crash reports.
- Screenshots/video if useful.

If the issue involves another mod, include that mod and version as well.

## Licensing
By contributing code to this repository, you agree that your contribution may be distributed under the repository's MIT license.

Do not submit code or assets that you do not own or have permission to redistribute.

## Questions
If you are unsure whether something belongs in the library, Gadgets & Gizmos, or in a consuming mod, open an issue first.
In general:
- If multiple mods could reasonably use it, it probably belongs in the library.
- If it only makes sense as part of one mod's gameplay/content, it probably belongs in that mod.
